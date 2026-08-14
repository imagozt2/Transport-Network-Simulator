package com.transport.simulator.entity;

import com.transport.simulator.enums.CompensatoryIssuanceStatus;
import com.transport.simulator.enums.CompensatoryDeliveryMethod;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "compensatory_ticket_issuances")
public class CompensatoryTicketIssuance extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private TicketProduct product;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 30)
    private CompensatoryDeliveryMethod deliveryMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_device_id")
    private Device targetDevice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_passenger_account_id")
    private PassengerAccount recipientPassenger;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_operator_id", nullable = false)
    private OperatorAccount requestedBy;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_ticket_id", unique = true)
    private Ticket issuedTicket;

    @Enumerated(EnumType.STRING)
    @Column(name = "issuance_status", nullable = false, length = 30)
    private CompensatoryIssuanceStatus status;

    @Column(nullable = false, length = 500)
    private String reason;

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

    @Column(name = "charged_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal chargedAmount = BigDecimal.ZERO;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    protected CompensatoryTicketIssuance() {
    }

    public CompensatoryTicketIssuance(
            String code, TicketProduct product, Device targetDevice,
            OperatorAccount requestedBy, String reason, LocalDateTime requestedAt
    ) {
        this.code = Objects.requireNonNull(code);
        this.product = Objects.requireNonNull(product);
        this.deliveryMethod = CompensatoryDeliveryMethod.PHYSICAL_DEVICE;
        this.targetDevice = Objects.requireNonNull(targetDevice);
        this.requestedBy = Objects.requireNonNull(requestedBy);
        this.reason = Objects.requireNonNull(reason);
        this.requestedAt = Objects.requireNonNull(requestedAt);
        this.status = CompensatoryIssuanceStatus.REQUESTED;
    }

    public CompensatoryTicketIssuance(
            String code, TicketProduct product, PassengerAccount recipientPassenger,
            OperatorAccount requestedBy, String reason, LocalDateTime requestedAt
    ) {
        this.code = Objects.requireNonNull(code);
        this.product = Objects.requireNonNull(product);
        this.deliveryMethod = CompensatoryDeliveryMethod.DIGITAL_WALLET;
        this.recipientPassenger = Objects.requireNonNull(recipientPassenger);
        this.requestedBy = Objects.requireNonNull(requestedBy);
        this.reason = Objects.requireNonNull(reason);
        this.requestedAt = Objects.requireNonNull(requestedAt);
        this.status = CompensatoryIssuanceStatus.REQUESTED;
    }

    public void configureSingleTrip(Station origin, Station destination, int stations) {
        originStation = origin;
        destinationStation = destination;
        stationCount = stations;
    }

    public void configureTripBalance(Integer trips) { selectedTrips = trips; }
    public void configureValidity(Integer days) { selectedDays = days; }
    public void configureMoneyBalance(BigDecimal amount) { rechargeAmount = amount; }

    public void beginProcessing(Ticket ticket) {
        issuedTicket = Objects.requireNonNull(ticket);
        status = CompensatoryIssuanceStatus.PROCESSING;
    }

    public void complete(LocalDateTime at) {
        if (status != CompensatoryIssuanceStatus.PROCESSING || issuedTicket == null) {
            throw new IllegalStateException("Only a processing issuance can be completed");
        }
        completedAt = Objects.requireNonNull(at);
        status = CompensatoryIssuanceStatus.COMPLETED;
    }

    public void fail(String reason, LocalDateTime at) {
        if (status == CompensatoryIssuanceStatus.COMPLETED) {
            throw new IllegalStateException("A completed issuance cannot fail");
        }
        failureReason = Objects.requireNonNull(reason);
        failedAt = Objects.requireNonNull(at);
        status = CompensatoryIssuanceStatus.FAILED;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public TicketProduct getProduct() { return product; }
    public CompensatoryDeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public Device getTargetDevice() { return targetDevice; }
    public PassengerAccount getRecipientPassenger() { return recipientPassenger; }
    public OperatorAccount getRequestedBy() { return requestedBy; }
    public Ticket getIssuedTicket() { return issuedTicket; }
    public CompensatoryIssuanceStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
