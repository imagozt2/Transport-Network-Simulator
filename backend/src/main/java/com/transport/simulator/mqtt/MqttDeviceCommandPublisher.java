package com.transport.simulator.mqtt;

import com.transport.simulator.entity.DeviceMqttCommand;
import com.transport.simulator.repository.DeviceMqttCommandRepository;
import com.transport.simulator.repository.DeviceMqttIdentityRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Component
public class MqttDeviceCommandPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttDeviceCommandPublisher.class);
    private final DeviceMqttCommandRepository commandRepository;
    private final DeviceMqttIdentityRepository identityRepository;
    private final ControlCenterMqttClient mqttClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MqttDeviceCommandPublisher(DeviceMqttCommandRepository commandRepository,
            DeviceMqttIdentityRepository identityRepository, ControlCenterMqttClient mqttClient,
            ObjectMapper objectMapper, Clock clock) {
        this.commandRepository = commandRepository;
        this.identityRepository = identityRepository;
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(DeviceMqttCommandCreated event) {
        DeviceMqttCommand command = commandRepository
                .findByIdForPublication(event.commandDatabaseId())
                .orElseThrow(() -> new IllegalStateException("MQTT command no longer exists"));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!command.canPublish(now)) {
            if (!command.getExpiresAt().isAfter(now)) {
                command.markPublicationFailed(now, "Command expired before publication");
            }
            return;
        }
        try {
            boolean activeIdentity = identityRepository.findByDeviceId(command.getDevice().getId())
                    .map(identity -> identity.canAuthenticate(now)
                            && identity.getMqttClientId().equals(command.getDevice().getCode()))
                    .orElse(false);
            if (!activeIdentity) throw new MqttTransportException("Target MQTT identity is inactive");
            mqttClient.publish(topic(command), envelope(command, now), 1, false);
            command.markPublished(now);
        } catch (RuntimeException exception) {
            command.markPublicationFailed(now, exception.getMessage());
            LOGGER.warn("MQTT command {} could not be published to machine {}: {}",
                    command.getCommandId(), command.getDevice().getCode(), exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String envelope(DeviceMqttCommand command, LocalDateTime sentAt) {
        try {
            Map<String, Object> payload = objectMapper.readValue(command.getPayloadJson(), Map.class);
            Map<String, Object> commandPayload = new LinkedHashMap<>(payload);
            commandPayload.put("commandId", command.getCommandId());
            commandPayload.put("expiresAt", instant(command.getExpiresAt()));
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("schemaVersion", 1);
            envelope.put("messageId", command.getMessageId());
            envelope.put("correlationId", null);
            envelope.put("type", command.getType().messageType());
            envelope.put("deviceCode", command.getDevice().getCode());
            envelope.put("occurredAt", instant(command.getRequestedAt()));
            envelope.put("sentAt", instant(sentAt));
            envelope.put("payload", commandPayload);
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception exception) {
            throw new MqttTransportException("MQTT command could not be serialized", exception);
        }
    }

    private String topic(DeviceMqttCommand command) {
        return "rmm/v1/devices/" + command.getDevice().getCode() + "/commands";
    }

    private String instant(LocalDateTime value) {
        ZoneId zone = clock.getZone();
        return value.atZone(zone).toInstant().toString();
    }
}
