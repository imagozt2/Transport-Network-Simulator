package com.transport.simulator.entity;

import com.transport.simulator.enums.TicketSupportStatus;
import com.transport.simulator.enums.TicketSupportType;
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
@Table(name = "ticket_supports")
public class TicketSupport extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "support_type", nullable = false, length = 20)
    private TicketSupportType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "support_status", nullable = false, length = 30)
    private TicketSupportStatus status;

    @Column(name = "serial_number", unique = true, length = 120)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_device_id")
    private Device issuedByDevice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_account_id")
    private PassengerAccount passengerAccount;

    @Column(name = "linking_code_hash", length = 255)
    private String linkingCodeHash;

    @Column(name = "linking_code_expires_at")
    private LocalDateTime linkingCodeExpiresAt;

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    @Column(name = "activated_at", nullable = false)
    private LocalDateTime activatedAt;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    protected TicketSupport() {
    }

    public static TicketSupport physical(
            String code,
            Ticket ticket,
            String serialNumber,
            Device issuingDevice,
            String linkingCodeHash,
            LocalDateTime linkingCodeExpiresAt,
            LocalDateTime issuedAt
    ) {
        TicketSupport support = base(code, ticket, TicketSupportType.PHYSICAL, issuedAt);
        support.serialNumber = requireText(serialNumber, "serialNumber");
        support.issuedByDevice = Objects.requireNonNull(issuingDevice, "issuingDevice is required");
        support.linkingCodeHash = requireText(linkingCodeHash, "linkingCodeHash");
        if (support.linkingCodeHash.length() < 20) {
            throw new IllegalArgumentException("linkingCodeHash must contain at least 20 characters");
        }
        support.linkingCodeExpiresAt = Objects.requireNonNull(
                linkingCodeExpiresAt, "linkingCodeExpiresAt is required"
        );
        if (linkingCodeExpiresAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException("linkingCodeExpiresAt cannot precede issuedAt");
        }
        return support;
    }

    public static TicketSupport digital(
            String code,
            Ticket ticket,
            PassengerAccount passenger,
            LocalDateTime issuedAt
    ) {
        TicketSupport support = base(code, ticket, TicketSupportType.DIGITAL, issuedAt);
        support.passengerAccount = Objects.requireNonNull(passenger, "passenger is required");
        support.linkedAt = issuedAt;
        return support;
    }

    private static TicketSupport base(
            String code,
            Ticket ticket,
            TicketSupportType type,
            LocalDateTime issuedAt
    ) {
        TicketSupport support = new TicketSupport();
        support.code = requireText(code, "code");
        support.ticket = Objects.requireNonNull(ticket, "ticket is required");
        support.type = type;
        support.status = TicketSupportStatus.ACTIVE;
        support.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt is required");
        support.activatedAt = issuedAt;
        return support;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public void linkToPassenger(PassengerAccount passenger, LocalDateTime at) {
        if (type != TicketSupportType.PHYSICAL || status != TicketSupportStatus.ACTIVE) {
            throw new IllegalStateException("Only an active physical support can be linked");
        }
        if (passengerAccount != null) {
            throw new IllegalStateException("Ticket support is already linked");
        }
        passengerAccount = Objects.requireNonNull(passenger, "passenger is required");
        linkedAt = Objects.requireNonNull(at, "linkedAt is required");
        linkingCodeHash = null;
        linkingCodeExpiresAt = null;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Ticket getTicket() { return ticket; }
    public TicketSupportType getType() { return type; }
    public TicketSupportStatus getStatus() { return status; }
    public String getSerialNumber() { return serialNumber; }
    public Device getIssuedByDevice() { return issuedByDevice; }
    public PassengerAccount getPassengerAccount() { return passengerAccount; }
    public String getLinkingCodeHash() { return linkingCodeHash; }
    public LocalDateTime getLinkingCodeExpiresAt() { return linkingCodeExpiresAt; }
    public LocalDateTime getLinkedAt() { return linkedAt; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
}
