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
@Table(name = "line_service_levels")
public class LineServiceLevel extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private TransportLine line;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_period_id", nullable = false)
    private ServicePeriod servicePeriod;

    @Column(name = "headway_seconds", nullable = false)
    private int headwaySeconds;

    @Column(nullable = false)
    private boolean active = true;

    protected LineServiceLevel() {
    }

    public Long getId() {
        return id;
    }

    public TransportLine getLine() {
        return line;
    }

    public ServicePeriod getServicePeriod() {
        return servicePeriod;
    }

    public int getHeadwaySeconds() {
        return headwaySeconds;
    }

    public boolean isActive() {
        return active;
    }
}
