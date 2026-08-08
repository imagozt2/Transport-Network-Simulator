package com.transport.simulator.entity;

import com.transport.simulator.enums.TicketQrUseClaimStatus;
import com.transport.simulator.enums.TicketQrValidationType;
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
@Table(name = "ticket_qr_use_claims")
public class TicketQrUseClaim extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "validation_reference", nullable = false, unique = true, length = 150)
    private String validationReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credential_id", nullable = false)
    private TicketQrCredential credential;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_type", nullable = false, length = 20)
    private TicketQrValidationType validationType;

    @Column(name = "device_code", nullable = false, length = 50)
    private String deviceCode;

    @Column(name = "station_code", nullable = false, length = 20)
    private String stationCode;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status", nullable = false, length = 20)
    private TicketQrUseClaimStatus status;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected TicketQrUseClaim() {
    }

    public TicketQrUseClaim(
            String validationReference,
            TicketQrCredential credential,
            TicketQrValidationType validationType,
            String deviceCode,
            String stationCode,
            String requestFingerprint,
            LocalDateTime receivedAt
    ) {
        this.validationReference = requireText(validationReference, "validationReference");
        this.credential = Objects.requireNonNull(credential, "credential is required");
        this.validationType = Objects.requireNonNull(validationType, "validationType is required");
        this.deviceCode = requireText(deviceCode, "deviceCode");
        this.stationCode = requireText(stationCode, "stationCode");
        this.requestFingerprint = requireText(requestFingerprint, "requestFingerprint");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt is required");
        status = TicketQrUseClaimStatus.RECEIVED;
    }

    public void complete(LocalDateTime completedAt) {
        if (status == TicketQrUseClaimStatus.COMPLETED) {
            return;
        }
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt is required");
        if (completedAt.isBefore(receivedAt)) {
            throw new IllegalArgumentException("completedAt cannot precede receivedAt");
        }
        status = TicketQrUseClaimStatus.COMPLETED;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public String getValidationReference() { return validationReference; }
    public TicketQrCredential getCredential() { return credential; }
    public TicketQrValidationType getValidationType() { return validationType; }
    public String getDeviceCode() { return deviceCode; }
    public String getStationCode() { return stationCode; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public TicketQrUseClaimStatus getStatus() { return status; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
