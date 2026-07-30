package com.transport.simulator.entity;

import com.transport.simulator.enums.OperatorAccountStatus;
import com.transport.simulator.enums.OperatorRole;
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
@Table(name = "operator_accounts")
public class OperatorAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 150)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator_role", nullable = false, length = 30)
    private OperatorRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    private OperatorAccountStatus status;

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

    protected OperatorAccount() {
    }

    public OperatorAccount(
            String username,
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            OperatorRole role
    ) {
        this.username = normalizeUsername(username);
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.firstName = requireText(firstName);
        this.lastName = requireText(lastName);
        this.role = Objects.requireNonNull(role);
        this.status = OperatorAccountStatus.ACTIVE;
    }

    public boolean releaseExpiredLock(LocalDateTime now) {
        if (status == OperatorAccountStatus.LOCKED
                && lockedUntil != null
                && !lockedUntil.isAfter(now)) {
            status = OperatorAccountStatus.ACTIVE;
            failedLoginAttempts = 0;
            lockedUntil = null;
            return true;
        }
        return false;
    }

    public void registerFailedLogin(LocalDateTime now, int maximumAttempts, int lockMinutes) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maximumAttempts) {
            status = OperatorAccountStatus.LOCKED;
            lockedUntil = now.plusMinutes(lockMinutes);
        }
    }

    public void registerSuccessfulLogin(LocalDateTime now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
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

    public OperatorRole getRole() {
        return role;
    }

    public OperatorAccountStatus getStatus() {
        return status;
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

    private static String normalizeUsername(String value) {
        return requireText(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeEmail(String value) {
        return requireText(value).toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Operator account fields cannot be blank");
        }
        return value.trim();
    }
}
