package com.transport.simulator.entity;

import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.enums.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "tickets")
public class Ticket extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(name = "qr_token", nullable = false, unique = true, length = 255)
    private String qrToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private TicketProduct product;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 40)
    private TicketProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_station_id")
    private Station originStation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_station_id")
    private Station destinationStation;

    @Column(name = "station_count")
    private Integer stationCount;

    @Column(name = "route_price_amount", precision = 10, scale = 2)
    private BigDecimal routePriceAmount;

    @Column(name = "purchased_trips")
    private Integer purchasedTrips;

    @Column(name = "remaining_trips")
    private Integer remainingTrips;

    @Column(name = "purchased_days")
    private Integer purchasedDays;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "balance_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_user_id")
    private PassengerAccount passengerAccount;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "last_recharged_at")
    private LocalDateTime lastRechargedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "status_changed_at", nullable = false)
    private LocalDateTime statusChangedAt;

    @Column(name = "exhausted_at")
    private LocalDateTime exhaustedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "lock_version", nullable = false)
    @Version
    private long lockVersion;

    protected Ticket() {
    }

    public Ticket(String code, String qrToken, TicketProduct product, LocalDateTime issuedAt) {
        this.code = Objects.requireNonNull(code);
        this.qrToken = Objects.requireNonNull(qrToken);
        this.product = Objects.requireNonNull(product);
        this.productType = product.getProductType();
        this.status = TicketStatus.ACTIVE;
        this.issuedAt = Objects.requireNonNull(issuedAt);
        this.statusChangedAt = issuedAt;
    }

    public void configureSingleTrip(Station origin, Station destination, int stations) {
        if (productType != TicketProductType.SINGLE_TRIP) {
            throw new IllegalStateException("Only single tickets have an origin and destination");
        }
        if (stations <= 0 || (origin != null && destination != null
                && origin.getCode().equals(destination.getCode()))) {
            throw new IllegalArgumentException("A single ticket requires a valid route");
        }
        originStation = Objects.requireNonNull(origin);
        destinationStation = Objects.requireNonNull(destination);
        stationCount = stations;
        routePriceAmount = product.getBasePrice().add(
                product.getPricePerStation().multiply(BigDecimal.valueOf(stations))
        );
    }

    public void configureTripBalance(int trips) {
        if (productType != TicketProductType.MULTI_TRIP) {
            throw new IllegalStateException("Only multi-trip tickets have a trip balance");
        }
        requireTripAmountWithinProductRange(trips);
        purchasedTrips = trips;
        remainingTrips = trips;
    }

    public void configureValidity(int days, LocalDateTime from) {
        if (productType != TicketProductType.TIME_PASS) {
            throw new IllegalStateException("Only time passes have a validity period");
        }
        requireDaysWithinProductRange(days);
        purchasedDays = days;
        validFrom = Objects.requireNonNull(from, "from is required");
        validUntil = from.plusDays(days);
    }

    public void configureMoneyBalance(BigDecimal amount) {
        if (productType != TicketProductType.SMART_BALANCE) {
            throw new IllegalStateException("Only smart-balance tickets have a money balance");
        }
        requireRechargeAmountWithinProductRange(amount);
        balanceAmount = amount;
    }

    public void assignPassenger(PassengerAccount passenger) {
        Objects.requireNonNull(passenger, "passenger is required");
        if (passengerAccount != null && passengerAccount != passenger) {
            throw new IllegalStateException("Ticket already belongs to another passenger");
        }
        passengerAccount = passenger;
    }

    public void exhaust(LocalDateTime at) {
        if (status != TicketStatus.ACTIVE) {
            throw new IllegalStateException("Only an active ticket can be exhausted");
        }
        status = TicketStatus.EXHAUSTED;
        exhaustedAt = Objects.requireNonNull(at, "at is required");
        statusChangedAt = at;
        lastUsedAt = at;
    }

    public void rechargeSingleTrip(
            Station origin,
            Station destination,
            int stations,
            LocalDateTime at
    ) {
        if (productType != TicketProductType.SINGLE_TRIP || status != TicketStatus.EXHAUSTED) {
            throw new IllegalStateException("Only an exhausted single ticket can be recharged");
        }
        configureSingleTrip(origin, destination, stations);
        status = TicketStatus.ACTIVE;
        exhaustedAt = null;
        statusChangedAt = Objects.requireNonNull(at, "at is required");
        lastRechargedAt = at;
    }

    public void consumeTrip(LocalDateTime at) {
        if (productType != TicketProductType.MULTI_TRIP || status != TicketStatus.ACTIVE
                || remainingTrips == null || remainingTrips <= 0) {
            throw new IllegalStateException("The ticket has no trip available for entry");
        }
        remainingTrips--;
        lastUsedAt = Objects.requireNonNull(at, "at is required");
        if (remainingTrips == 0) {
            status = TicketStatus.EXHAUSTED;
            exhaustedAt = at;
            statusChangedAt = at;
        }
    }

    public void rechargeTrips(int trips, LocalDateTime at) {
        if (productType != TicketProductType.MULTI_TRIP) {
            throw new IllegalStateException("Only multi-trip tickets accept trip recharges");
        }
        if (status != TicketStatus.ACTIVE && status != TicketStatus.EXHAUSTED) {
            throw new IllegalStateException("The ticket cannot be recharged in its current status");
        }
        requireTripAmountWithinProductRange(trips);
        int currentTrips = remainingTrips == null ? 0 : remainingTrips;
        int resultingTrips = Math.addExact(currentTrips, trips);
        if (product.getMaxTrips() != null && resultingTrips > product.getMaxTrips()) {
            throw new IllegalArgumentException("The resulting trip balance exceeds the product maximum");
        }
        purchasedTrips = resultingTrips;
        remainingTrips = resultingTrips;
        status = TicketStatus.ACTIVE;
        exhaustedAt = null;
        statusChangedAt = Objects.requireNonNull(at, "at is required");
        lastRechargedAt = at;
    }

    private void requireTripAmountWithinProductRange(int trips) {
        if (product.getMinTrips() == null || product.getMaxTrips() == null
                || trips < product.getMinTrips() || trips > product.getMaxTrips()) {
            throw new IllegalArgumentException(
                    "Trips must be between " + product.getMinTrips() + " and " + product.getMaxTrips()
            );
        }
    }

    public void refreshTimePassStatus(LocalDateTime at) {
        if (productType != TicketProductType.TIME_PASS) {
            throw new IllegalStateException("Only time passes have a validity period");
        }
        Objects.requireNonNull(at, "at is required");
        if (status == TicketStatus.ACTIVE && validUntil != null && at.isAfter(validUntil)) {
            status = TicketStatus.EXPIRED;
            expiredAt = at;
            statusChangedAt = at;
        }
    }

    public boolean isValidAt(LocalDateTime at) {
        Objects.requireNonNull(at, "at is required");
        return productType == TicketProductType.TIME_PASS
                && active
                && status == TicketStatus.ACTIVE
                && validFrom != null
                && validUntil != null
                && !at.isBefore(validFrom)
                && !at.isAfter(validUntil);
    }

    public void renewValidity(int days, LocalDateTime at) {
        if (productType != TicketProductType.TIME_PASS) {
            throw new IllegalStateException("Only time passes accept validity renewals");
        }
        if (status != TicketStatus.ACTIVE && status != TicketStatus.EXPIRED) {
            throw new IllegalStateException("The ticket cannot be renewed in its current status");
        }
        requireDaysWithinProductRange(days);
        Objects.requireNonNull(at, "at is required");
        if (status == TicketStatus.ACTIVE && validUntil != null && !at.isAfter(validUntil)) {
            purchasedDays = Math.addExact(purchasedDays == null ? 0 : purchasedDays, days);
            validUntil = validUntil.plusDays(days);
        } else {
            purchasedDays = days;
            validFrom = at;
            validUntil = at.plusDays(days);
        }
        status = TicketStatus.ACTIVE;
        expiredAt = null;
        statusChangedAt = at;
        lastRechargedAt = at;
    }

    public void recordUse(LocalDateTime at) {
        if (status != TicketStatus.ACTIVE && status != TicketStatus.EXPIRED) {
            throw new IllegalStateException("The ticket cannot record a use in its current status");
        }
        lastUsedAt = Objects.requireNonNull(at, "at is required");
    }

    private void requireDaysWithinProductRange(int days) {
        if (product.getMinDays() == null || product.getMaxDays() == null
                || days < product.getMinDays() || days > product.getMaxDays()) {
            throw new IllegalArgumentException(
                    "Days must be between " + product.getMinDays() + " and " + product.getMaxDays()
            );
        }
    }

    public boolean canStartSmartBalanceJourney() {
        return productType == TicketProductType.SMART_BALANCE
                && active
                && status == TicketStatus.ACTIVE
                && balanceAmount.compareTo(minimumSmartBalanceFare()) >= 0;
    }

    public BigDecimal calculateSmartBalanceFare(int stations) {
        if (productType != TicketProductType.SMART_BALANCE) {
            throw new IllegalStateException("Only smart-balance tickets calculate journey fares");
        }
        if (stations <= 0) {
            throw new IllegalArgumentException("stations must be positive");
        }
        return product.getBasePrice().add(
                product.getPricePerStation().multiply(BigDecimal.valueOf(stations))
        );
    }

    public void deductSmartBalanceFare(BigDecimal fare, LocalDateTime at) {
        if (productType != TicketProductType.SMART_BALANCE || status != TicketStatus.ACTIVE) {
            throw new IllegalStateException("The ticket cannot pay a smart-balance fare");
        }
        Objects.requireNonNull(fare, "fare is required");
        if (fare.signum() < 0 || balanceAmount.compareTo(fare) < 0) {
            throw new IllegalStateException("The ticket has insufficient balance for the journey fare");
        }
        balanceAmount = balanceAmount.subtract(fare);
        lastUsedAt = Objects.requireNonNull(at, "at is required");
        if (balanceAmount.compareTo(minimumSmartBalanceFare()) < 0) {
            status = TicketStatus.EXHAUSTED;
            exhaustedAt = at;
            statusChangedAt = at;
        }
    }

    public void rechargeMoneyBalance(BigDecimal amount, LocalDateTime at) {
        if (productType != TicketProductType.SMART_BALANCE) {
            throw new IllegalStateException("Only smart-balance tickets accept money recharges");
        }
        if (status != TicketStatus.ACTIVE && status != TicketStatus.EXHAUSTED) {
            throw new IllegalStateException("The ticket cannot be recharged in its current status");
        }
        requireRechargeAmountWithinProductRange(amount);
        balanceAmount = balanceAmount.add(amount);
        status = TicketStatus.ACTIVE;
        exhaustedAt = null;
        statusChangedAt = Objects.requireNonNull(at, "at is required");
        lastRechargedAt = at;
    }

    private BigDecimal minimumSmartBalanceFare() {
        return product.getBasePrice().add(product.getPricePerStation());
    }

    private void requireRechargeAmountWithinProductRange(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount is required");
        if (product.getMinRechargeAmount() == null || product.getMaxRechargeAmount() == null
                || amount.compareTo(product.getMinRechargeAmount()) < 0
                || amount.compareTo(product.getMaxRechargeAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Recharge amount must be between " + product.getMinRechargeAmount()
                            + " and " + product.getMaxRechargeAmount()
            );
        }
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getQrToken() { return qrToken; }
    public TicketProduct getProduct() { return product; }
    public TicketProductType getProductType() { return productType; }
    public TicketStatus getStatus() { return status; }
    public Station getOriginStation() { return originStation; }
    public Station getDestinationStation() { return destinationStation; }
    public Integer getStationCount() { return stationCount; }
    public BigDecimal getRoutePriceAmount() { return routePriceAmount; }
    public Integer getPurchasedTrips() { return purchasedTrips; }
    public Integer getRemainingTrips() { return remainingTrips; }
    public Integer getPurchasedDays() { return purchasedDays; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public String getCurrency() { return currency; }
    public PassengerAccount getPassengerAccount() { return passengerAccount; }
    public boolean isActive() { return active; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getLastRechargedAt() { return lastRechargedAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
}
