package com.transport.simulator.entity;

import com.transport.simulator.enums.TicketJourneyStatus;
import com.transport.simulator.enums.TicketQrValidationType;
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
@Table(name = "ticket_journeys")
public class TicketJourney extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_account_id")
    private PassengerAccount passengerAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_station_id", nullable = false)
    private Station entryStation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_station_id")
    private Station exitStation;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_validation_id")
    private TicketValidation entryValidation;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_validation_id")
    private TicketValidation exitValidation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TicketJourneyStatus status;

    @Column(name = "station_count")
    private Integer stationCount;

    @Column(name = "fare_amount", precision = 10, scale = 2)
    private BigDecimal fareAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "forced_closed_at")
    private LocalDateTime forcedClosedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "duration_seconds", insertable = false, updatable = false)
    private Integer durationSeconds;

    protected TicketJourney() {
    }

    public TicketJourney(
            String code,
            Ticket ticket,
            Station entryStation,
            LocalDateTime openedAt
    ) {
        this.code = requireText(code, "code");
        this.ticket = Objects.requireNonNull(ticket, "ticket is required");
        passengerAccount = ticket.getPassengerAccount();
        this.entryStation = Objects.requireNonNull(entryStation, "entryStation is required");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt is required");
        status = TicketJourneyStatus.OPEN;
        currency = ticket.getCurrency();
    }

    public void close(
            Station station,
            int stations,
            BigDecimal fare,
            LocalDateTime closedAt
    ) {
        if (status != TicketJourneyStatus.OPEN) {
            throw new IllegalStateException("Only an open journey can be closed");
        }
        Objects.requireNonNull(closedAt, "closedAt is required");
        if (closedAt.isBefore(openedAt)) {
            throw new IllegalArgumentException("closedAt cannot precede openedAt");
        }
        if (stations <= 0) {
            throw new IllegalArgumentException("stations must be positive");
        }
        exitStation = Objects.requireNonNull(station, "station is required");
        stationCount = stations;
        fareAmount = Objects.requireNonNull(fare, "fare is required");
        this.closedAt = closedAt;
        status = TicketJourneyStatus.CLOSED;
    }

    public void forceClose(LocalDateTime forcedClosedAt) {
        if (status != TicketJourneyStatus.OPEN) {
            throw new IllegalStateException("Only an open journey can be force-closed");
        }
        Objects.requireNonNull(forcedClosedAt, "forcedClosedAt is required");
        if (forcedClosedAt.isBefore(openedAt)) {
            throw new IllegalArgumentException("forcedClosedAt cannot precede openedAt");
        }
        this.forcedClosedAt = forcedClosedAt;
        status = TicketJourneyStatus.FORCED_CLOSED;
    }

    public void assignPassenger(PassengerAccount passenger) {
        Objects.requireNonNull(passenger, "passenger is required");
        if (passengerAccount != null && passengerAccount != passenger
                && !Objects.equals(passengerAccount.getId(), passenger.getId())) {
            throw new IllegalStateException("The journey already belongs to another passenger");
        }
        passengerAccount = passenger;
    }

    public void attachEntryValidation(TicketValidation validation) {
        Objects.requireNonNull(validation, "validation is required");
        if (entryValidation != null && entryValidation != validation) {
            throw new IllegalStateException("The journey already has an entry validation");
        }
        if (validation.getType() != TicketQrValidationType.ENTRY
                || validation.getJourney() != this) {
            throw new IllegalArgumentException("The validation does not belong to this journey entry");
        }
        entryValidation = validation;
    }

    public void attachExitValidation(TicketValidation validation) {
        Objects.requireNonNull(validation, "validation is required");
        if (exitValidation != null && exitValidation != validation) {
            throw new IllegalStateException("The journey already has an exit validation");
        }
        if (validation.getType() != TicketQrValidationType.EXIT
                || validation.getJourney() != this
                || status != TicketJourneyStatus.CLOSED) {
            throw new IllegalArgumentException("The validation does not belong to this journey exit");
        }
        exitValidation = validation;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Ticket getTicket() { return ticket; }
    public PassengerAccount getPassengerAccount() { return passengerAccount; }
    public Station getEntryStation() { return entryStation; }
    public Station getExitStation() { return exitStation; }
    public TicketValidation getEntryValidation() { return entryValidation; }
    public TicketValidation getExitValidation() { return exitValidation; }
    public TicketJourneyStatus getStatus() { return status; }
    public Integer getStationCount() { return stationCount; }
    public BigDecimal getFareAmount() { return fareAmount; }
    public String getCurrency() { return currency; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public LocalDateTime getForcedClosedAt() { return forcedClosedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public Integer getDurationSeconds() { return durationSeconds; }

    public boolean isAnomalous() {
        return status == TicketJourneyStatus.FORCED_CLOSED
                || status == TicketJourneyStatus.CANCELLED;
    }
}
