package com.transport.simulator.entity;

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
@Table(name = "incident_status_changes")
public class IncidentStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_operator_id", nullable = false)
    private OperatorAccount changedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private IncidentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private IncidentStatus newStatus;

    @Column(name = "change_note", length = 500)
    private String changeNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected IncidentStatusChange() {
    }

    public IncidentStatusChange(
            Incident incident,
            OperatorAccount changedBy,
            IncidentStatus previousStatus,
            IncidentStatus newStatus,
            String changeNote,
            LocalDateTime createdAt
    ) {
        this.incident = Objects.requireNonNull(incident);
        this.changedBy = Objects.requireNonNull(changedBy);
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus);
        this.changeNote = normalize(changeNote);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public Incident getIncident() { return incident; }
    public OperatorAccount getChangedBy() { return changedBy; }
    public IncidentStatus getPreviousStatus() { return previousStatus; }
    public IncidentStatus getNewStatus() { return newStatus; }
    public String getChangeNote() { return changeNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
