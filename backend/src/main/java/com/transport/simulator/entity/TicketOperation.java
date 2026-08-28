package com.transport.simulator.entity;

import com.transport.simulator.enums.TicketOperationSource;
import com.transport.simulator.enums.TicketOperationType;
import com.transport.simulator.enums.TicketStatus;
import com.transport.simulator.service.model.TicketSnapshot;
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
@Table(name = "ticket_operations")
public class TicketOperation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 40)
    private TicketOperationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_source", nullable = false, length = 40)
    private TicketOperationSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "support_id")
    private TicketSupport support;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_id")
    private TicketJourney journey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_account_id")
    private PassengerAccount passengerAccount;

    @Column(name = "external_reference", length = 150)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 40)
    private TicketStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_status", nullable = false, length = 40)
    private TicketStatus resultingStatus;

    @Column(name = "balance_before", precision = 10, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "remaining_trips_before")
    private Integer remainingTripsBefore;

    @Column(name = "remaining_trips_after")
    private Integer remainingTripsAfter;

    @Column(name = "valid_from_before")
    private LocalDateTime validFromBefore;

    @Column(name = "valid_until_before")
    private LocalDateTime validUntilBefore;

    @Column(name = "valid_from_after")
    private LocalDateTime validFromAfter;

    @Column(name = "valid_until_after")
    private LocalDateTime validUntilAfter;

    @Column(name = "operation_amount", precision = 10, scale = 2)
    private BigDecimal operationAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected TicketOperation() {
    }

    public TicketOperation(
            String code,
            Ticket ticket,
            TicketOperationType type,
            TicketOperationSource source,
            TicketSnapshot before,
            TicketSnapshot after,
            BigDecimal amount,
            LocalDateTime occurredAt
    ) {
        this.code = Objects.requireNonNull(code);
        this.ticket = Objects.requireNonNull(ticket);
        this.type = Objects.requireNonNull(type);
        this.source = Objects.requireNonNull(source);
        this.occurredAt = Objects.requireNonNull(occurredAt);
        currency = ticket.getCurrency();
        operationAmount = amount;
        applyBefore(before);
        applyAfter(Objects.requireNonNull(after));
    }

    private void applyBefore(TicketSnapshot snapshot) {
        if (snapshot == null) { return; }
        previousStatus = snapshot.status();
        balanceBefore = snapshot.balance();
        remainingTripsBefore = snapshot.remainingTrips();
        validFromBefore = snapshot.validFrom();
        validUntilBefore = snapshot.validUntil();
    }

    private void applyAfter(TicketSnapshot snapshot) {
        resultingStatus = snapshot.status();
        balanceAfter = snapshot.balance();
        remainingTripsAfter = snapshot.remainingTrips();
        validFromAfter = snapshot.validFrom();
        validUntilAfter = snapshot.validUntil();
    }

    public void relateToSupport(TicketSupport value) { support = value; }
    public void relateToPurchase(Purchase value) { purchase = value; }
    public void relateToJourney(TicketJourney value, Station atStation) {
        journey = value;
        station = atStation;
    }
    public void recordContext(Device value, PassengerAccount passenger, String reference) {
        device = value;
        passengerAccount = passenger;
        externalReference = reference;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Ticket getTicket() { return ticket; }
    public TicketOperationType getType() { return type; }
    public TicketOperationSource getSource() { return source; }
    public TicketSupport getSupport() { return support; }
    public Purchase getPurchase() { return purchase; }
    public TicketJourney getJourney() { return journey; }
    public Station getStation() { return station; }
    public Device getDevice() { return device; }
    public PassengerAccount getPassengerAccount() { return passengerAccount; }
    public String getExternalReference() { return externalReference; }
    public TicketStatus getPreviousStatus() { return previousStatus; }
    public TicketStatus getResultingStatus() { return resultingStatus; }
    public BigDecimal getBalanceBefore() { return balanceBefore; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public Integer getRemainingTripsBefore() { return remainingTripsBefore; }
    public Integer getRemainingTripsAfter() { return remainingTripsAfter; }
    public LocalDateTime getValidFromBefore() { return validFromBefore; }
    public LocalDateTime getValidUntilBefore() { return validUntilBefore; }
    public LocalDateTime getValidFromAfter() { return validFromAfter; }
    public LocalDateTime getValidUntilAfter() { return validUntilAfter; }
    public BigDecimal getOperationAmount() { return operationAmount; }
    public String getCurrency() { return currency; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
