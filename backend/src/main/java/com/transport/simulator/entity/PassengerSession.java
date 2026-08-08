package com.transport.simulator.entity;

import com.transport.simulator.enums.PassengerDevicePlatform;
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
@Table(name = "passenger_sessions")
public class PassengerSession extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_account_id", nullable = false)
    private PassengerAccount passengerAccount;

    @Column(name = "installation_id", nullable = false, length = 36)
    private String installationId;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PassengerDevicePlatform platform;

    @Column(name = "access_token_hash", nullable = false, unique = true, length = 64)
    private String accessTokenHash;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "access_token_expires_at", nullable = false)
    private LocalDateTime accessTokenExpiresAt;

    @Column(name = "refresh_token_expires_at", nullable = false)
    private LocalDateTime refreshTokenExpiresAt;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason", length = 100)
    private String revocationReason;

    protected PassengerSession() {
    }

    public PassengerSession(
            PassengerAccount passengerAccount,
            String installationId,
            String deviceName,
            PassengerDevicePlatform platform,
            String accessTokenHash,
            String refreshTokenHash,
            LocalDateTime accessTokenExpiresAt,
            LocalDateTime refreshTokenExpiresAt,
            LocalDateTime issuedAt
    ) {
        this.passengerAccount = Objects.requireNonNull(passengerAccount);
        this.installationId = requireText(installationId);
        this.deviceName = requireText(deviceName);
        this.platform = Objects.requireNonNull(platform);
        this.accessTokenHash = requireText(accessTokenHash);
        this.refreshTokenHash = requireText(refreshTokenHash);
        this.accessTokenExpiresAt = Objects.requireNonNull(accessTokenExpiresAt);
        this.refreshTokenExpiresAt = Objects.requireNonNull(refreshTokenExpiresAt);
        this.lastUsedAt = Objects.requireNonNull(issuedAt);
    }

    public boolean canUseAccessToken(LocalDateTime now) {
        return revokedAt == null && accessTokenExpiresAt.isAfter(now);
    }

    public boolean canRefresh(LocalDateTime now, String requestedInstallationId) {
        return revokedAt == null
                && refreshTokenExpiresAt.isAfter(now)
                && installationId.equals(requestedInstallationId);
    }

    public void rotate(
            String accessHash,
            String refreshHash,
            LocalDateTime accessExpiry,
            LocalDateTime refreshExpiry,
            LocalDateTime now
    ) {
        if (revokedAt != null) {
            throw new IllegalStateException("A revoked passenger session cannot be renewed");
        }
        accessTokenHash = requireText(accessHash);
        refreshTokenHash = requireText(refreshHash);
        accessTokenExpiresAt = Objects.requireNonNull(accessExpiry);
        refreshTokenExpiresAt = Objects.requireNonNull(refreshExpiry);
        lastUsedAt = Objects.requireNonNull(now);
    }

    public void recordUse(LocalDateTime now) {
        lastUsedAt = Objects.requireNonNull(now);
    }

    public void revoke(LocalDateTime now, String reason) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(now);
            revocationReason = requireText(reason);
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Passenger session fields cannot be blank");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public PassengerAccount getPassengerAccount() { return passengerAccount; }
    public String getInstallationId() { return installationId; }
    public String getDeviceName() { return deviceName; }
    public PassengerDevicePlatform getPlatform() { return platform; }
    public LocalDateTime getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public LocalDateTime getRefreshTokenExpiresAt() { return refreshTokenExpiresAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
}
