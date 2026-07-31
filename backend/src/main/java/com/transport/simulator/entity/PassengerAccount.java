package com.transport.simulator.entity;

import com.transport.simulator.enums.PassengerAccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "passenger_accounts")
public class PassengerAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "public_id",
            nullable = false,
            unique = true,
            length = 36,
            columnDefinition = "CHAR(36)"
    )
    private String publicId;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 150)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    private PassengerAccountStatus status;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(
            name = "password_changed_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime passwordChangedAt;

    protected PassengerAccount() {
    }

    public PassengerAccount(
            String publicId,
            String email,
            String passwordHash,
            String firstName,
            String lastName
    ) {
        this.publicId = Objects.requireNonNull(publicId);
        this.email = requireText(email).toLowerCase(Locale.ROOT);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.firstName = requireText(firstName);
        this.lastName = requireText(lastName);
        this.status = PassengerAccountStatus.ACTIVE;
    }

    public PassengerAccountStatus changeStatus(PassengerAccountStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Passenger account status is required");
        }
        if (status == newStatus) {
            throw new IllegalStateException("Passenger account already has the requested status");
        }
        if (status == PassengerAccountStatus.DISABLED
                && newStatus != PassengerAccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "A disabled passenger account must be activated before it can be blocked"
            );
        }

        PassengerAccountStatus previousStatus = status;
        status = newStatus;
        return previousStatus;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public PassengerAccountStatus getStatus() {
        return status;
    }

    public LocalDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Passenger account fields cannot be blank");
        }
        return value.trim();
    }
}
