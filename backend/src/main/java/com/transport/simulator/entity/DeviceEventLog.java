package com.transport.simulator.entity;

import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "operational_logs")
public class DeviceEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_origin", nullable = false, length = 50)
    private LogOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private DeviceEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LogSeverity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compensatory_issuance_id")
    private CompensatoryTicketIssuance compensatoryIssuance;

    @Column(name = "external_reference", length = 150)
    private String externalReference;

    @Column(name = "payload_json", columnDefinition = "json")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    protected DeviceEventLog() {
    }

    public DeviceEventLog(
            LogOrigin origin,
            DeviceEventType eventType,
            LogSeverity severity,
            String message,
            Device device,
            LocalDateTime occurredAt,
            String externalReference,
            String payloadJson
    ) {
        this.origin = origin;
        this.eventType = eventType;
        this.severity = severity;
        this.message = message;
        this.device = device;
        this.station = device.getStation();
        this.occurredAt = occurredAt;
        this.externalReference = externalReference;
        this.payloadJson = payloadJson;
    }

    public void linkCompensatoryIssuance(
            CompensatoryTicketIssuance issuance,
            Ticket issuedTicket
    ) {
        compensatoryIssuance = issuance;
        ticket = issuedTicket;
    }

    @PrePersist
    void setReceptionTime() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public LogOrigin getOrigin() {
        return origin;
    }

    public DeviceEventType getEventType() {
        return eventType;
    }

    public LogSeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public Device getDevice() {
        return device;
    }

    public Station getStation() {
        return station;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}
