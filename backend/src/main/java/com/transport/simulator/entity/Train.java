package com.transport.simulator.entity;

import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.enums.FleetRole;
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

@Entity
@Table(name = "trains")
public class Train extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_model_id", nullable = false)
    private TrainModel model;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_depot_id", nullable = false)
    private Depot homeDepot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_line_id", nullable = false)
    private TransportLine assignedLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_line_id")
    private TransportLine currentLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_station_id")
    private Station currentStation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_station_id")
    private Station nextStation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_depot_id")
    private Depot currentDepot;

    private Short direction;

    @Column(name = "progress_percentage", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private short progressPercentage;

    @Column(name = "last_position_update_at")
    private LocalDateTime lastPositionUpdateAt;

    @Column(name = "service_started_at")
    private LocalDateTime serviceStartedAt;

    @Column(name = "service_ended_at")
    private LocalDateTime serviceEndedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "fleet_role", nullable = false, length = 30)
    private FleetRole fleetRole;

    @Column(name = "dispatch_order")
    private Integer dispatchOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TrainStatus status = TrainStatus.DEPOT;

    @Column(nullable = false)
    private boolean active = true;

    protected Train() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public TrainModel getModel() {
        return model;
    }

    public Depot getHomeDepot() {
        return homeDepot;
    }

    public TransportLine getAssignedLine() {
        return assignedLine;
    }

    public TransportLine getCurrentLine() {
        return currentLine;
    }

    public Station getCurrentStation() {
        return currentStation;
    }

    public Station getNextStation() {
        return nextStation;
    }

    public Depot getCurrentDepot() {
        return currentDepot;
    }

    public Short getDirection() {
        return direction;
    }

    public short getProgressPercentage() {
        return progressPercentage;
    }

    public LocalDateTime getLastPositionUpdateAt() {
        return lastPositionUpdateAt;
    }

    public LocalDateTime getServiceStartedAt() {
        return serviceStartedAt;
    }

    public LocalDateTime getServiceEndedAt() {
        return serviceEndedAt;
    }

    public FleetRole getFleetRole() {
        return fleetRole;
    }

    public Integer getDispatchOrder() {
        return dispatchOrder;
    }

    public TrainStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return active;
    }
}
