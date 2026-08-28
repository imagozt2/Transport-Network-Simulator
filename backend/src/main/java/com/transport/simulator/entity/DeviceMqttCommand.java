package com.transport.simulator.entity;

import com.transport.simulator.enums.DeviceMqttCommandStatus;
import com.transport.simulator.enums.DeviceMqttCommandType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "device_mqtt_commands")
public class DeviceMqttCommand extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "command_id", nullable = false, unique = true, length = 80)
    private String commandId;

    @Column(name = "message_id", nullable = false, unique = true, length = 36)
    private String messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 50)
    private DeviceMqttCommandType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_status", nullable = false, length = 30)
    private DeviceMqttCommandStatus status;

    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "publication_attempts", nullable = false)
    private int publicationAttempts;

    @Column(name = "last_publication_error", length = 500)
    private String lastPublicationError;

    protected DeviceMqttCommand() {}

    public DeviceMqttCommand(String commandId, String messageId, Device device,
            DeviceMqttCommandType type, String payloadJson,
            LocalDateTime requestedAt, LocalDateTime expiresAt) {
        this.commandId = requireText(commandId);
        this.messageId = requireText(messageId);
        this.device = Objects.requireNonNull(device);
        this.type = Objects.requireNonNull(type);
        this.payloadJson = requireText(payloadJson);
        this.requestedAt = Objects.requireNonNull(requestedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        if (!expiresAt.isAfter(requestedAt)) throw new IllegalArgumentException("Command expiry must be later than its request");
        this.status = DeviceMqttCommandStatus.PENDING;
    }

    public boolean canPublish(LocalDateTime now) {
        return (status == DeviceMqttCommandStatus.PENDING
                || status == DeviceMqttCommandStatus.PUBLISH_FAILED)
                && expiresAt.isAfter(now);
    }

    public void markPublished(LocalDateTime now) {
        publicationAttempts++;
        publishedAt = Objects.requireNonNull(now);
        lastPublicationError = null;
        status = DeviceMqttCommandStatus.PUBLISHED;
    }

    public void markPublicationFailed(LocalDateTime now, String error) {
        publicationAttempts++;
        if (!expiresAt.isAfter(now)) status = DeviceMqttCommandStatus.EXPIRED;
        else status = DeviceMqttCommandStatus.PUBLISH_FAILED;
        lastPublicationError = abbreviate(error);
    }

    public void acknowledge(DeviceMqttCommandStatus acknowledgedStatus, LocalDateTime now, String error) {
        Objects.requireNonNull(now);
        if (acknowledgedStatus != DeviceMqttCommandStatus.RECEIVED
                && acknowledgedStatus != DeviceMqttCommandStatus.PROCESSING
                && acknowledgedStatus != DeviceMqttCommandStatus.COMPLETED
                && acknowledgedStatus != DeviceMqttCommandStatus.FAILED
                && acknowledgedStatus != DeviceMqttCommandStatus.REJECTED) {
            throw new IllegalArgumentException("Unsupported command acknowledgement status");
        }
        boolean terminal = status == DeviceMqttCommandStatus.COMPLETED
                || status == DeviceMqttCommandStatus.FAILED
                || status == DeviceMqttCommandStatus.REJECTED;
        if (terminal && acknowledgedStatus != status) {
            return;
        }
        if (acknowledgementOrder(acknowledgedStatus) < acknowledgementOrder(status)) {
            return;
        }
        status = acknowledgedStatus;
        lastPublicationError = acknowledgedStatus == DeviceMqttCommandStatus.FAILED
                || acknowledgedStatus == DeviceMqttCommandStatus.REJECTED
                ? abbreviate(error) : null;
    }

    private int acknowledgementOrder(DeviceMqttCommandStatus value) {
        return switch (value) {
            case PENDING, PUBLISH_FAILED, PUBLISHED -> 0;
            case RECEIVED -> 1;
            case PROCESSING -> 2;
            case COMPLETED, FAILED, REJECTED, EXPIRED -> 3;
        };
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Command fields cannot be blank");
        return value.trim();
    }
    private static String abbreviate(String value) {
        String normalized = value == null || value.isBlank() ? "Unknown MQTT publication error" : value.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
    public Long getId() { return id; }
    public String getCommandId() { return commandId; }
    public String getMessageId() { return messageId; }
    public Device getDevice() { return device; }
    public DeviceMqttCommandType getType() { return type; }
    public DeviceMqttCommandStatus getStatus() { return status; }
    public String getPayloadJson() { return payloadJson; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public int getPublicationAttempts() { return publicationAttempts; }
    public String getLastPublicationError() { return lastPublicationError; }
}
