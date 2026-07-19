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
@Table(name = "depots")
public class Depot extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "track_count", nullable = false)
    private int trackCount;

    @Column(name = "trains_per_track", nullable = false)
    private int trainsPerTrack;

    @Column(nullable = false)
    private boolean active = true;

    protected Depot() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Station getStation() {
        return station;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getTrackCount() {
        return trackCount;
    }

    public int getTrainsPerTrack() {
        return trainsPerTrack;
    }

    public boolean isActive() {
        return active;
    }
}
