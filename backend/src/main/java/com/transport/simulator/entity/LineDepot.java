package com.transport.simulator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "line_depots")
public class LineDepot extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private TransportLine line;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @Column(name = "dispatch_priority", nullable = false)
    private int dispatchPriority;

    @Column(name = "dispatch_enabled", nullable = false)
    private boolean dispatchEnabled = true;

    @Column(name = "reception_enabled", nullable = false)
    private boolean receptionEnabled = true;

    @Column(nullable = false)
    private boolean active = true;

    protected LineDepot() {
    }

    public Long getId() {
        return id;
    }

    public TransportLine getLine() {
        return line;
    }

    public Depot getDepot() {
        return depot;
    }

    public int getDispatchPriority() {
        return dispatchPriority;
    }

    public boolean isDispatchEnabled() {
        return dispatchEnabled;
    }

    public boolean isReceptionEnabled() {
        return receptionEnabled;
    }

    public boolean isActive() {
        return active;
    }
}
