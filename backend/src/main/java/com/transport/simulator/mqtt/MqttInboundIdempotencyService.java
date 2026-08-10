package com.transport.simulator.mqtt;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.MqttInboundMessage;
import com.transport.simulator.repository.MqttInboundMessageRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MqttInboundIdempotencyService {
    private final MqttInboundMessageRepository repository;
    private final EntityManager entityManager;
    private final Clock clock;

    public MqttInboundIdempotencyService(MqttInboundMessageRepository repository,
            EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MqttIdempotencyClaim claim(String messageId, Long deviceId,
            String topic, byte[] payload) {
        String fingerprint = fingerprint(topic, payload);
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findByMessageIdForUpdate(messageId)
                .map(existing -> claimExisting(existing, deviceId, topic, fingerprint, now))
                .orElseGet(() -> {
                    Device device = entityManager.getReference(Device.class, deviceId);
                    repository.save(new MqttInboundMessage(
                            messageId, device, topic, fingerprint, now));
                    return MqttIdempotencyClaim.PROCESS;
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String messageId) {
        message(messageId).complete(LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reject(String messageId, String error) {
        message(messageId).reject(LocalDateTime.now(clock), error);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String messageId, String error) {
        message(messageId).fail(error);
    }

    private MqttIdempotencyClaim claimExisting(MqttInboundMessage existing,
            Long deviceId, String topic, String fingerprint, LocalDateTime now) {
        if (!existing.sameRequest(deviceId, topic, fingerprint)) {
            throw new MqttMessageIdReuseException();
        }
        if (existing.shouldSkip(now)) {
            existing.recordDuplicate(now);
            return MqttIdempotencyClaim.DUPLICATE;
        }
        existing.retry(now);
        return MqttIdempotencyClaim.PROCESS;
    }

    private MqttInboundMessage message(String messageId) {
        return repository.findByMessageIdForUpdate(messageId)
                .orElseThrow(() -> new IllegalStateException("MQTT inbox message does not exist"));
    }

    private String fingerprint(String topic, byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(topic.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("MQTT payload fingerprint could not be calculated", exception);
        }
    }
}
