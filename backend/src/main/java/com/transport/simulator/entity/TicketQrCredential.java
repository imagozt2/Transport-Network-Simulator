package com.transport.simulator.entity;

import com.transport.simulator.enums.TicketQrCredentialStatus;
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
import java.util.UUID;

@Entity
@Table(name = "ticket_qr_credentials")
public class TicketQrCredential extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credential_id", nullable = false, unique = true, length = 36)
    private UUID credentialId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "support_id", nullable = false)
    private TicketSupport support;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_status", nullable = false, length = 30)
    private TicketQrCredentialStatus status;

    @Column(name = "wrapper_version", nullable = false)
    private int wrapperVersion;

    @Column(name = "signing_key_id", nullable = false, length = 100)
    private String signingKeyId;

    @Column(name = "token_fingerprint", nullable = false, unique = true, length = 64)
    private String tokenFingerprint;

    @Column(name = "qr_value", nullable = false, length = 4096)
    private String qrValue;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_by_credential_id")
    private TicketQrCredential supersededBy;

    protected TicketQrCredential() {
    }

    public static TicketQrCredential active(
            UUID credentialId,
            Ticket ticket,
            TicketSupport support,
            int wrapperVersion,
            String signingKeyId,
            String tokenFingerprint,
            String qrValue,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        TicketQrCredential credential = new TicketQrCredential();
        credential.credentialId = credentialId;
        credential.ticket = ticket;
        credential.support = support;
        credential.status = TicketQrCredentialStatus.ACTIVE;
        credential.wrapperVersion = wrapperVersion;
        credential.signingKeyId = signingKeyId;
        credential.tokenFingerprint = tokenFingerprint;
        credential.qrValue = qrValue;
        credential.issuedAt = issuedAt;
        credential.expiresAt = expiresAt;
        return credential;
    }

    public Long getId() { return id; }
    public UUID getCredentialId() { return credentialId; }
    public Ticket getTicket() { return ticket; }
    public TicketSupport getSupport() { return support; }
    public TicketQrCredentialStatus getStatus() { return status; }
    public int getWrapperVersion() { return wrapperVersion; }
    public String getSigningKeyId() { return signingKeyId; }
    public String getTokenFingerprint() { return tokenFingerprint; }
    public String getQrValue() { return qrValue; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public String getRevocationReason() { return revocationReason; }
    public TicketQrCredential getSupersededBy() { return supersededBy; }
}
