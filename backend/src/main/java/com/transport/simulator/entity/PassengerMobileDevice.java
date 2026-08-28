package com.transport.simulator.entity;

import com.transport.simulator.enums.PassengerDevicePlatform;
import com.transport.simulator.enums.PassengerMobileDeviceStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "passenger_mobile_devices")
public class PassengerMobileDevice extends AuditableEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_account_id", nullable = false)
    private PassengerAccount passengerAccount;

    @Column(name = "installation_id", nullable = false, unique = true, length = 36)
    private String installationId;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PassengerDevicePlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_status", nullable = false, length = 20)
    private PassengerMobileDeviceStatus status;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected PassengerMobileDevice() {}

    public PassengerMobileDevice(PassengerAccount account, String installationId,
            String deviceName, PassengerDevicePlatform platform, LocalDateTime now) {
        this.publicId = UUID.randomUUID().toString();
        this.passengerAccount = Objects.requireNonNull(account);
        this.installationId = requireText(installationId);
        this.deviceName = requireText(deviceName);
        this.platform = Objects.requireNonNull(platform);
        this.status = PassengerMobileDeviceStatus.ACTIVE;
        this.registeredAt = Objects.requireNonNull(now);
        this.lastSeenAt = now;
    }

    public void recordUse(String name, PassengerDevicePlatform requestedPlatform, LocalDateTime now) {
        if (status != PassengerMobileDeviceStatus.ACTIVE || platform != requestedPlatform) {
            throw new IllegalStateException("Passenger mobile device cannot be reused");
        }
        deviceName = requireText(name);
        lastSeenAt = Objects.requireNonNull(now);
    }

    public void revoke(LocalDateTime now) {
        status = PassengerMobileDeviceStatus.REVOKED;
        revokedAt = Objects.requireNonNull(now);
        lastSeenAt = now;
    }

    public boolean isActive() { return status == PassengerMobileDeviceStatus.ACTIVE; }
    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Device fields cannot be blank");
        return value.trim();
    }
    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public PassengerAccount getPassengerAccount() { return passengerAccount; }
    public String getInstallationId() { return installationId; }
    public String getDeviceName() { return deviceName; }
    public PassengerDevicePlatform getPlatform() { return platform; }
    public PassengerMobileDeviceStatus getStatus() { return status; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
}
