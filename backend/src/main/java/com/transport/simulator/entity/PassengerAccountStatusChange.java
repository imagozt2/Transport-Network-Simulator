package com.transport.simulator.entity;

import com.transport.simulator.enums.PassengerAccountStatus;
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
@Table(name = "passenger_account_status_changes")
public class PassengerAccountStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_account_id", nullable = false)
    private PassengerAccount passengerAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_operator_id", nullable = false)
    private OperatorAccount changedByOperator;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 30)
    private PassengerAccountStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private PassengerAccountStatus newStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PassengerAccountStatusChange() {
    }

    public PassengerAccountStatusChange(
            PassengerAccount passengerAccount,
            OperatorAccount changedByOperator,
            PassengerAccountStatus previousStatus,
            PassengerAccountStatus newStatus,
            String reason
    ) {
        this.passengerAccount = Objects.requireNonNull(passengerAccount);
        this.changedByOperator = Objects.requireNonNull(changedByOperator);
        this.previousStatus = Objects.requireNonNull(previousStatus);
        this.newStatus = Objects.requireNonNull(newStatus);
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public PassengerAccount getPassengerAccount() {
        return passengerAccount;
    }

    public OperatorAccount getChangedByOperator() {
        return changedByOperator;
    }

    public PassengerAccountStatus getPreviousStatus() {
        return previousStatus;
    }

    public PassengerAccountStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
