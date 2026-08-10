package com.transport.simulator.entity;

import com.transport.simulator.enums.DeviceMqttAuthenticationMode;
import com.transport.simulator.enums.DeviceMqttIdentityStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "device_mqtt_identities")
public class DeviceMqttIdentity extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    private Device device;

    @Column(name = "instance_id", nullable = false, unique = true, length = 36)
    private String instanceId;

    @Column(name = "mqtt_client_id", nullable = false, unique = true, length = 100)
    private String mqttClientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "authentication_mode", nullable = false, length = 20)
    private DeviceMqttAuthenticationMode authenticationMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_status", nullable = false, length = 20)
    private DeviceMqttIdentityStatus status;

    @Column(name = "certificate_serial", unique = true, length = 128)
    private String certificateSerial;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "last_authenticated_at")
    private LocalDateTime lastAuthenticatedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected DeviceMqttIdentity() {}

    public DeviceMqttIdentity(Device device, String mqttClientId,
            DeviceMqttAuthenticationMode mode, String certificateSerial,
            LocalDateTime validFrom, LocalDateTime validUntil) {
        this.device = Objects.requireNonNull(device);
        this.instanceId = UUID.randomUUID().toString();
        this.mqttClientId = requireText(mqttClientId);
        this.authenticationMode = Objects.requireNonNull(mode);
        this.certificateSerial = normalize(certificateSerial);
        this.validFrom = Objects.requireNonNull(validFrom);
        this.validUntil = validUntil;
        this.status = DeviceMqttIdentityStatus.ACTIVE;
    }

    public boolean canAuthenticate(LocalDateTime now) {
        return status == DeviceMqttIdentityStatus.ACTIVE
                && device.isActive()
                && !validFrom.isAfter(now)
                && (validUntil == null || validUntil.isAfter(now));
    }

    public void recordAuthentication(LocalDateTime now) {
        if (!canAuthenticate(now)) throw new IllegalStateException("Inactive MQTT identity");
        if (lastAuthenticatedAt == null || !now.isBefore(lastAuthenticatedAt.plusMinutes(1))) {
            lastAuthenticatedAt = now;
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("MQTT identity is required");
        return value.trim();
    }
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    public Long getId() { return id; }
    public Device getDevice() { return device; }
    public String getInstanceId() { return instanceId; }
    public String getMqttClientId() { return mqttClientId; }
    public DeviceMqttAuthenticationMode getAuthenticationMode() { return authenticationMode; }
    public DeviceMqttIdentityStatus getStatus() { return status; }
    public String getCertificateSerial() { return certificateSerial; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public LocalDateTime getLastAuthenticatedAt() { return lastAuthenticatedAt; }
}
