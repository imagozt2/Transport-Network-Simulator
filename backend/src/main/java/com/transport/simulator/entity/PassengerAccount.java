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

    @Column(name = "preferred_locale", nullable = false, length = 10)
    private String preferredLocale;

    @Column(name = "accepted_terms_version", length = 30)
    private String acceptedTermsVersion;

    @Column(name = "accepted_terms_at")
    private LocalDateTime acceptedTermsAt;

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
            updatable = true
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
        this.preferredLocale = "es-ES";
    }

    public static PassengerAccount register(
            String publicId,
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String preferredLocale,
            String acceptedTermsVersion,
            LocalDateTime acceptedTermsAt
    ) {
        PassengerAccount account = new PassengerAccount(
                publicId, email, passwordHash, firstName, lastName
        );
        account.status = PassengerAccountStatus.PENDING_VERIFICATION;
        account.preferredLocale = requireText(preferredLocale);
        account.acceptedTermsVersion = requireText(acceptedTermsVersion);
        account.acceptedTermsAt = Objects.requireNonNull(
                acceptedTermsAt,
                "acceptedTermsAt is required"
        );
        return account;
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

    public boolean isTemporarilyLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerFailedLogin(LocalDateTime now, int maximumAttempts, int lockMinutes) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maximumAttempts) {
            lockedUntil = now.plusMinutes(lockMinutes);
            failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(LocalDateTime now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void verifyEmail(LocalDateTime now) {
        if (emailVerifiedAt != null) {
            return;
        }
        emailVerifiedAt = Objects.requireNonNull(now);
        if (status == PassengerAccountStatus.PENDING_VERIFICATION) {
            status = PassengerAccountStatus.ACTIVE;
        }
    }

    public void changePassword(String encodedPassword, LocalDateTime now) {
        passwordHash = requireText(encodedPassword);
        passwordChangedAt = Objects.requireNonNull(now);
        failedLoginAttempts = 0;
        lockedUntil = null;
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

    public String getPreferredLocale() { return preferredLocale; }
    public String getAcceptedTermsVersion() { return acceptedTermsVersion; }
    public LocalDateTime getAcceptedTermsAt() { return acceptedTermsAt; }

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
