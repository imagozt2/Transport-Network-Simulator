package com.transport.simulator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "train_models",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_train_models_model_series",
                columnNames = {"manufacturer", "model_name", "series"}
        )
)
public class TrainModel extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String manufacturer;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(nullable = false, length = 100)
    private String series;

    @Column(name = "car_count", nullable = false)
    private int carCount;

    @Column(name = "capacity_passengers", nullable = false)
    private int passengerCapacity;

    @Column(name = "max_speed_kmh", nullable = false)
    private int maximumSpeedKmh;

    @Column(nullable = false)
    private boolean active = true;

    protected TrainModel() {
    }

    public Long getId() {
        return id;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModelName() {
        return modelName;
    }

    public String getSeries() {
        return series;
    }

    public int getCarCount() {
        return carCount;
    }

    public int getPassengerCapacity() {
        return passengerCapacity;
    }

    public int getMaximumSpeedKmh() {
        return maximumSpeedKmh;
    }

    public boolean isActive() {
        return active;
    }
}
