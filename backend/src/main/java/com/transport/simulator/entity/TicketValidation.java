package com.transport.simulator.entity;

import com.transport.simulator.enums.TicketQrValidationType;
import com.transport.simulator.enums.TicketValidationStatus;
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

@Entity
@Table(name = "ticket_validations")
public class TicketValidation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journey_id")
    private TicketJourney journey;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_type", nullable = false, length = 40)
    private TicketQrValidationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 40)
    private TicketValidationStatus status;

    @Column(name = "rejection_reason", length = 80)
    private String rejectionReason;

    @Column(name = "qr_token", length = 255)
    private String qrToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_user_id")
    private PassengerAccount passenger;

    @Column(length = 500)
    private String message;

    @Column(name = "fare_amount", precision = 10, scale = 2)
    private BigDecimal fareAmount;

    @Column(name = "balance_before", precision = 10, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "remaining_trips_before")
    private Integer remainingTripsBefore;

    @Column(name = "remaining_trips_after")
    private Integer remainingTripsAfter;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "external_reference", length = 150)
    private String externalReference;

    @Column(name = "validated_at", nullable = false)
    private LocalDateTime validatedAt;

    protected TicketValidation() {
    }

    public static TicketValidation accepted(String code, String reference,
            TicketQrValidationType type, Ticket ticket, TicketJourney journey,
            Station station, Device device, String qrToken, TicketSnapshot before,
            TicketSnapshot after, LocalDateTime at) {
        TicketValidation validation = base(code, reference, type, station, device, at);
        validation.status = TicketValidationStatus.ACCEPTED;
        validation.ticket = ticket;
        validation.journey = journey;
        validation.passenger = ticket.getPassengerAccount();
        validation.qrToken = qrToken;
        validation.message = "Validation accepted";
        validation.fareAmount = journey.getFareAmount();
        validation.balanceBefore = before.balance();
        validation.balanceAfter = after.balance();
        validation.remainingTripsBefore = before.remainingTrips();
        validation.remainingTripsAfter = after.remainingTrips();
        validation.validFrom = after.validFrom();
        validation.validUntil = after.validUntil();
        return validation;
    }

    public static TicketValidation rejected(String code, String reference,
            TicketQrValidationType type, Station station, Device device,
            String reason, String message, LocalDateTime at) {
        TicketValidation validation = base(code, reference, type, station, device, at);
        validation.status = TicketValidationStatus.REJECTED;
        validation.rejectionReason = reason;
        validation.message = message;
        return validation;
    }

    private static TicketValidation base(String code, String reference,
            TicketQrValidationType type, Station station, Device device, LocalDateTime at) {
        TicketValidation validation = new TicketValidation();
        validation.code = code;
        validation.externalReference = reference;
        validation.type = type;
        validation.station = station;
        validation.device = device;
        validation.validatedAt = at;
        return validation;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Ticket getTicket() { return ticket; }
    public TicketJourney getJourney() { return journey; }
    public TicketQrValidationType getType() { return type; }
    public TicketValidationStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Station getStation() { return station; }
    public Device getDevice() { return device; }
    public BigDecimal getFareAmount() { return fareAmount; }
    public BigDecimal getBalanceBefore() { return balanceBefore; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public Integer getRemainingTripsBefore() { return remainingTripsBefore; }
    public Integer getRemainingTripsAfter() { return remainingTripsAfter; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public String getExternalReference() { return externalReference; }
    public LocalDateTime getValidatedAt() { return validatedAt; }
}
