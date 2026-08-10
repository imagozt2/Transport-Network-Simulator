package com.transport.simulator.entity;

import com.transport.simulator.enums.PaymentMethod;
import com.transport.simulator.enums.PurchaseOrigin;
import com.transport.simulator.enums.PurchaseStatus;
import com.transport.simulator.enums.PurchaseType;
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
@Table(name = "purchases")
public class Purchase extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", nullable = false, length = 40)
    private PurchaseType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private TicketProduct product;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_origin", nullable = false, length = 40)
    private PurchaseOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_status", nullable = false, length = 40)
    private PurchaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 40)
    private PaymentMethod paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_station_id")
    private Station originStation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_station_id")
    private Station destinationStation;

    @Column(name = "station_count")
    private Integer stationCount;

    @Column(name = "selected_trips")
    private Integer selectedTrips;

    @Column(name = "selected_days")
    private Integer selectedDays;

    @Column(name = "recharge_amount", precision = 10, scale = 2)
    private BigDecimal rechargeAmount;

    @Column(name = "subtotal_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "external_reference", unique = true, length = 150)
    private String externalReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_user_id")
    private PassengerAccount passengerAccount;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected Purchase() {
    }

    public static Purchase completedRecharge(
            String code,
            Ticket ticket,
            PurchaseOrigin origin,
            PaymentMethod paymentMethod,
            String externalReference,
            Device device,
            PassengerAccount passenger,
            BigDecimal totalAmount,
            LocalDateTime completedAt
    ) {
        Purchase purchase = new Purchase();
        purchase.code = requireText(code, "code");
        purchase.ticket = Objects.requireNonNull(ticket, "ticket is required");
        purchase.product = ticket.getProduct();
        purchase.type = PurchaseType.RECHARGE;
        purchase.origin = Objects.requireNonNull(origin, "origin is required");
        purchase.status = PurchaseStatus.COMPLETED;
        purchase.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod is required");
        purchase.externalReference = requireText(externalReference, "externalReference");
        purchase.device = device;
        purchase.station = device == null ? null : device.getStation();
        purchase.passengerAccount = passenger;
        purchase.subtotalAmount = requireNonNegative(totalAmount);
        purchase.totalAmount = totalAmount;
        purchase.currency = ticket.getCurrency();
        purchase.requestedAt = Objects.requireNonNull(completedAt, "completedAt is required");
        purchase.completedAt = completedAt;
        return purchase;
    }

    public static Purchase completedPurchase(
            String code,
            Ticket ticket,
            PaymentMethod paymentMethod,
            String externalReference,
            PassengerAccount passenger,
            BigDecimal totalAmount,
            LocalDateTime completedAt
    ) {
        Purchase purchase = new Purchase();
        purchase.code = requireText(code, "code");
        purchase.ticket = Objects.requireNonNull(ticket, "ticket is required");
        purchase.product = ticket.getProduct();
        purchase.type = PurchaseType.PURCHASE;
        purchase.origin = PurchaseOrigin.RMM_APP;
        purchase.status = PurchaseStatus.COMPLETED;
        purchase.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod is required");
        purchase.externalReference = requireText(externalReference, "externalReference");
        purchase.passengerAccount = Objects.requireNonNull(passenger, "passenger is required");
        purchase.subtotalAmount = requireNonNegative(totalAmount);
        purchase.totalAmount = totalAmount;
        purchase.currency = ticket.getCurrency();
        purchase.requestedAt = Objects.requireNonNull(completedAt, "completedAt is required");
        purchase.completedAt = completedAt;
        return purchase;
    }

    public void configureSingleTrip(Station origin, Station destination, int stations) {
        originStation = Objects.requireNonNull(origin);
        destinationStation = Objects.requireNonNull(destination);
        stationCount = stations;
    }

    public void configureTrips(int trips) { selectedTrips = trips; }
    public void configureDays(int days) { selectedDays = days; }
    public void configureMoney(BigDecimal amount) { rechargeAmount = requireNonNegative(amount); }

    private static BigDecimal requireNonNegative(BigDecimal value) {
        Objects.requireNonNull(value, "amount is required");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public PurchaseType getType() { return type; }
    public Ticket getTicket() { return ticket; }
    public PurchaseOrigin getOrigin() { return origin; }
    public PurchaseStatus getStatus() { return status; }
    public TicketProduct getProduct() { return product; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public PassengerAccount getPassengerAccount() { return passengerAccount; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getExternalReference() { return externalReference; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
