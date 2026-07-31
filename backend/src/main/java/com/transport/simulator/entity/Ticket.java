package com.transport.simulator.entity;

import com.transport.simulator.enums.TicketProductType;
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

    @Column(nullable = false, length = 40)
    private String status;

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

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    protected Ticket() {
    }

    public Ticket(String code, String qrToken, TicketProduct product, LocalDateTime issuedAt) {
        this.code = Objects.requireNonNull(code);
        this.qrToken = Objects.requireNonNull(qrToken);
        this.product = Objects.requireNonNull(product);
        this.productType = product.getProductType();
        this.status = "ACTIVE";
        this.issuedAt = Objects.requireNonNull(issuedAt);
    }

    public void configureSingleTrip(Station origin, Station destination, int stations) {
        originStation = Objects.requireNonNull(origin);
        destinationStation = Objects.requireNonNull(destination);
        stationCount = stations;
        routePriceAmount = BigDecimal.ZERO;
    }

    public void configureTripBalance(int trips) {
        purchasedTrips = trips;
        remainingTrips = trips;
    }

    public void configureValidity(int days, LocalDateTime from) {
        purchasedDays = days;
        validFrom = from;
        validUntil = from.plusDays(days);
    }

    public void configureMoneyBalance(BigDecimal amount) {
        balanceAmount = amount;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getQrToken() { return qrToken; }
    public TicketProduct getProduct() { return product; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
}
