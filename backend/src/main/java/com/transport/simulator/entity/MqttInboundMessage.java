package com.transport.simulator.entity;

import com.transport.simulator.enums.MqttInboundProcessingStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "mqtt_inbound_messages")
public class MqttInboundMessage extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "message_id", nullable = false, unique = true, length = 36)
    private String messageId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;
    @Column(nullable = false, length = 255)
    private String topic;
    @Column(name = "payload_fingerprint", nullable = false, length = 64)
    private String payloadFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private MqttInboundProcessingStatus status;
    @Column(name = "processing_attempts", nullable = false)
    private int processingAttempts;
    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    @Column(name = "last_duplicate_at")
    private LocalDateTime lastDuplicateAt;
    @Column(name = "last_error", length = 500)
    private String lastError;

    protected MqttInboundMessage() {}

    public MqttInboundMessage(String messageId, Device device, String topic,
            String fingerprint, LocalDateTime receivedAt) {
        this.messageId = requireText(messageId);
        this.device = Objects.requireNonNull(device);
        this.topic = requireText(topic);
        this.payloadFingerprint = requireText(fingerprint);
        this.receivedAt = Objects.requireNonNull(receivedAt);
        this.status = MqttInboundProcessingStatus.PROCESSING;
        this.processingAttempts = 1;
    }

    public boolean sameRequest(Long deviceId, String topic, String fingerprint) {
        return device.getId().equals(deviceId) && this.topic.equals(topic)
                && payloadFingerprint.equals(fingerprint);
    }

    public boolean shouldSkip(LocalDateTime now) {
        return status == MqttInboundProcessingStatus.PROCESSED
                || status == MqttInboundProcessingStatus.REJECTED
                || (status == MqttInboundProcessingStatus.PROCESSING
                    && receivedAt.plusMinutes(2).isAfter(now));
    }

    public void recordDuplicate(LocalDateTime now) {
        duplicateCount++;
        lastDuplicateAt = Objects.requireNonNull(now);
    }

    public void retry(LocalDateTime now) {
        processingAttempts++;
        status = MqttInboundProcessingStatus.PROCESSING;
        receivedAt = Objects.requireNonNull(now);
        lastError = null;
    }

    public void complete(LocalDateTime now) {
        status = MqttInboundProcessingStatus.PROCESSED;
        processedAt = Objects.requireNonNull(now);
        lastError = null;
    }

    public void reject(LocalDateTime now, String error) {
        status = MqttInboundProcessingStatus.REJECTED;
        processedAt = Objects.requireNonNull(now);
        lastError = abbreviate(error);
    }

    public void fail(String error) {
        status = MqttInboundProcessingStatus.FAILED;
        processedAt = null;
        lastError = abbreviate(error);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("MQTT inbox fields cannot be blank");
        return value.trim();
    }
    private static String abbreviate(String value) {
        String normalized = value == null || value.isBlank() ? "Unknown MQTT processing error" : value.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
    public Long getId() { return id; }
    public String getMessageId() { return messageId; }
    public MqttInboundProcessingStatus getStatus() { return status; }
    public int getProcessingAttempts() { return processingAttempts; }
    public int getDuplicateCount() { return duplicateCount; }
}
