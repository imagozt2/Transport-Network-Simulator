package com.transport.simulator.entity;

import com.transport.simulator.enums.PassengerAccountTokenType;
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
@Table(name = "passenger_account_tokens")
public class PassengerAccountToken extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_account_id", nullable = false)
    private PassengerAccount passengerAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 30)
    private PassengerAccountTokenType type;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    protected PassengerAccountToken() {
    }

    public PassengerAccountToken(
            PassengerAccount passengerAccount,
            PassengerAccountTokenType type,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        this.passengerAccount = Objects.requireNonNull(passengerAccount);
        this.type = Objects.requireNonNull(type);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public boolean canBeUsedAt(LocalDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void consume(LocalDateTime now) {
        if (!canBeUsedAt(now)) {
            throw new IllegalStateException("Passenger account token is no longer valid");
        }
        usedAt = now;
    }

    public PassengerAccount getPassengerAccount() { return passengerAccount; }
    public PassengerAccountTokenType getType() { return type; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
}
