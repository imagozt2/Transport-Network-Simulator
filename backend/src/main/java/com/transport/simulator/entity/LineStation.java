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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "line_stations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_line_stations_line_station",
                        columnNames = {"line_id", "station_id"}
                ),
                @UniqueConstraint(
                        name = "uk_line_stations_line_order",
                        columnNames = {"line_id", "station_order"}
                )
        }
)
public class LineStation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private TransportLine line;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "station_order", nullable = false)
    private int stationOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected LineStation() {
    }

    public LineStation(TransportLine line, Station station, int stationOrder) {
        this.line = line;
        this.station = station;
        this.stationOrder = stationOrder;
    }

    public Long getId() {
        return id;
    }

    public TransportLine getLine() {
        return line;
    }

    public Station getStation() {
        return station;
    }

    public int getStationOrder() {
        return stationOrder;
    }

    public boolean isActive() {
        return active;
    }
}
