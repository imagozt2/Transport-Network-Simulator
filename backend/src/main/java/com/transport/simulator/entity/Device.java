package com.transport.simulator.entity;

import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.DeviceMqttPresence;
import com.transport.simulator.enums.DeviceOperationalState;
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
@Table(name = "devices")
public class Device extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 30)
    private DeviceType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeviceStatus status = DeviceStatus.OFFLINE;

    @Column(name = "last_connection_at")
    private LocalDateTime lastConnectionAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "mqtt_presence", length = 20)
    private DeviceMqttPresence mqttPresence;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_state", length = 30)
    private DeviceOperationalState operationalState;

    @Column(name = "service_mode", length = 30)
    private String serviceMode;

    @Column(name = "software_version", length = 50)
    private String softwareVersion;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "last_presence_at")
    private LocalDateTime lastPresenceAt;

    @Column(name = "last_status_at")
    private LocalDateTime lastStatusAt;

    @Column(nullable = false)
    private boolean active = true;

    protected Device() {
    }

    public void recordEvent(DeviceStatus resultingStatus, LocalDateTime connectionAt) {
        status = Objects.requireNonNull(resultingStatus, "resultingStatus is required");
        Objects.requireNonNull(connectionAt, "connectionAt is required");

        if (lastConnectionAt == null || connectionAt.isAfter(lastConnectionAt)) {
            lastConnectionAt = connectionAt;
        }
    }

    public boolean recordMqttPresence(DeviceMqttPresence presence, LocalDateTime changedAt) {
        Objects.requireNonNull(presence);
        Objects.requireNonNull(changedAt);
        if (lastPresenceAt != null && changedAt.isBefore(lastPresenceAt)) return false;
        mqttPresence = presence;
        lastPresenceAt = changedAt;
        if (presence == DeviceMqttPresence.OFFLINE) {
            status = DeviceStatus.OFFLINE;
        } else {
            status = aggregateOperationalStatus();
            recordConnection(changedAt);
        }
        return true;
    }

    public boolean recordMqttStatus(DeviceOperationalState state, String serviceMode,
            String softwareVersion, long uptimeSeconds, LocalDateTime occurredAt) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(occurredAt);
        if (uptimeSeconds < 0) throw new IllegalArgumentException("uptimeSeconds cannot be negative");
        if (lastStatusAt != null && occurredAt.isBefore(lastStatusAt)) return false;
        operationalState = state;
        this.serviceMode = normalize(serviceMode);
        this.softwareVersion = normalize(softwareVersion);
        this.uptimeSeconds = uptimeSeconds;
        lastStatusAt = occurredAt;
        if (mqttPresence != DeviceMqttPresence.OFFLINE) {
            status = aggregateOperationalStatus();
            recordConnection(occurredAt);
        }
        return true;
    }

    private DeviceStatus aggregateOperationalStatus() {
        if (operationalState == DeviceOperationalState.MAINTENANCE) return DeviceStatus.MAINTENANCE;
        if (operationalState == DeviceOperationalState.OUT_OF_SERVICE) return DeviceStatus.ERROR;
        return DeviceStatus.ONLINE;
    }

    private void recordConnection(LocalDateTime at) {
        if (lastConnectionAt == null || at.isAfter(lastConnectionAt)) lastConnectionAt = at;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    public DeviceType getType() {
        return type;
    }

    public Station getStation() {
        return station;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public LocalDateTime getLastConnectionAt() {
        return lastConnectionAt;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMqttManaged() { return lastPresenceAt != null || lastStatusAt != null; }
    public DeviceMqttPresence getMqttPresence() { return mqttPresence; }
    public DeviceOperationalState getOperationalState() { return operationalState; }
    public String getServiceMode() { return serviceMode; }
    public String getSoftwareVersion() { return softwareVersion; }
    public Long getUptimeSeconds() { return uptimeSeconds; }
    public LocalDateTime getLastPresenceAt() { return lastPresenceAt; }
    public LocalDateTime getLastStatusAt() { return lastStatusAt; }
}
