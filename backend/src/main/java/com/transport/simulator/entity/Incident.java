package com.transport.simulator.entity;

import com.transport.simulator.enums.IncidentCategory;
import com.transport.simulator.enums.IncidentPriority;
import com.transport.simulator.enums.IncidentStatus;
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
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "incidents")
public class Incident extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_category", nullable = false, length = 30)
    private IncidentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_status", nullable = false, length = 30)
    private IncidentStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_operator_id", nullable = false)
    private OperatorAccount createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_operator_id")
    private OperatorAccount assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affected_line_id")
    private TransportLine affectedLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affected_station_id")
    private Station affectedStation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affected_train_id")
    private Train affectedTrain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affected_device_id")
    private Device affectedDevice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "affected_depot_id")
    private Depot affectedDepot;

    @Column(name = "resolution_summary", columnDefinition = "TEXT")
    private String resolutionSummary;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    protected Incident() {
    }

    public Incident(
            String code,
            String title,
            String description,
            IncidentCategory category,
            IncidentPriority priority,
            OperatorAccount createdBy,
            LocalDateTime openedAt
    ) {
        this.code = requireText(code);
        this.title = requireText(title);
        this.description = requireText(description);
        this.category = Objects.requireNonNull(category);
        this.priority = Objects.requireNonNull(priority);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.openedAt = Objects.requireNonNull(openedAt);
        this.status = IncidentStatus.OPEN;
    }

    public void updateDetails(
            String title,
            String description,
            IncidentCategory category,
            IncidentPriority priority
    ) {
        requireEditable();
        this.title = requireText(title);
        this.description = requireText(description);
        this.category = Objects.requireNonNull(category);
        this.priority = Objects.requireNonNull(priority);
    }

    public void assignTo(OperatorAccount operator, LocalDateTime assignedAt) {
        requireEditable();
        this.assignedTo = operator;
        this.assignedAt = operator == null ? null : Objects.requireNonNull(assignedAt);
    }

    public void setAffectedResources(
            TransportLine line,
            Station station,
            Train train,
            Device device,
            Depot depot
    ) {
        requireEditable();
        affectedLine = line;
        affectedStation = station;
        affectedTrain = train;
        affectedDevice = device;
        affectedDepot = depot;
    }

    public IncidentStatus changeStatus(
            IncidentStatus newStatus,
            String resolution,
            LocalDateTime changedAt
    ) {
        Objects.requireNonNull(newStatus);
        Objects.requireNonNull(changedAt);
        if (status == newStatus) {
            throw new IllegalStateException("Incident already has the requested status");
        }
        if (!allowsTransition(status, newStatus)) {
            throw new IllegalStateException("Unsupported incident status transition");
        }
        if (newStatus == IncidentStatus.RESOLVED) {
            resolutionSummary = requireText(resolution);
            resolvedAt = changedAt;
            closedAt = null;
        } else if (newStatus == IncidentStatus.CLOSED) {
            if (resolutionSummary == null) {
                throw new IllegalStateException("A resolved incident is required before closing");
            }
            closedAt = changedAt;
        } else if (newStatus == IncidentStatus.IN_PROGRESS && status == IncidentStatus.RESOLVED) {
            resolvedAt = null;
            resolutionSummary = null;
        } else if (newStatus == IncidentStatus.CANCELLED) {
            closedAt = changedAt;
        }
        IncidentStatus previous = status;
        status = newStatus;
        return previous;
    }

    private boolean allowsTransition(IncidentStatus current, IncidentStatus next) {
        return switch (current) {
            case OPEN -> next == IncidentStatus.IN_PROGRESS || next == IncidentStatus.CANCELLED;
            case IN_PROGRESS -> next == IncidentStatus.RESOLVED || next == IncidentStatus.CANCELLED;
            case RESOLVED -> next == IncidentStatus.IN_PROGRESS || next == IncidentStatus.CLOSED;
            case CLOSED, CANCELLED -> false;
        };
    }

    private void requireEditable() {
        if (status == IncidentStatus.CLOSED || status == IncidentStatus.CANCELLED) {
            throw new IllegalStateException("Closed or cancelled incidents cannot be edited");
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Incident fields cannot be blank");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public IncidentCategory getCategory() { return category; }
    public IncidentPriority getPriority() { return priority; }
    public IncidentStatus getStatus() { return status; }
    public OperatorAccount getCreatedBy() { return createdBy; }
    public OperatorAccount getAssignedTo() { return assignedTo; }
    public TransportLine getAffectedLine() { return affectedLine; }
    public Station getAffectedStation() { return affectedStation; }
    public Train getAffectedTrain() { return affectedTrain; }
    public Device getAffectedDevice() { return affectedDevice; }
    public Depot getAffectedDepot() { return affectedDepot; }
    public String getResolutionSummary() { return resolutionSummary; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
}
