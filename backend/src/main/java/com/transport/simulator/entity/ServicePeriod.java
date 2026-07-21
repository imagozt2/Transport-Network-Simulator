package com.transport.simulator.entity;

import com.transport.simulator.enums.ServicePeriodType;
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
import java.time.LocalTime;

@Entity
@Table(name = "service_periods")
public class ServicePeriod extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_calendar_id", nullable = false)
    private ServiceCalendar serviceCalendar;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 30)
    private ServicePeriodType periodType;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "period_order", nullable = false)
    private int periodOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected ServicePeriod() {
    }

    public Long getId() {
        return id;
    }

    public ServiceCalendar getServiceCalendar() {
        return serviceCalendar;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public ServicePeriodType getPeriodType() {
        return periodType;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getPeriodOrder() {
        return periodOrder;
    }

    public boolean isActive() {
        return active;
    }
}
