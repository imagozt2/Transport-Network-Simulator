package com.transport.simulator.service.deviceevent;

import com.transport.simulator.enums.DeviceMqttPresence;
import com.transport.simulator.enums.DeviceOperationalState;
import com.transport.simulator.mqtt.AuthenticatedMqttMessage;
import com.transport.simulator.mqtt.AuthenticatedMqttMessageRouter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MqttDeviceStateReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttDeviceStateReceiver.class);
    private final MqttDeviceStateService stateService;
    private final ObjectMapper objectMapper;

    public MqttDeviceStateReceiver(AuthenticatedMqttMessageRouter router,
            MqttDeviceStateService stateService, ObjectMapper objectMapper) {
        this.stateService = stateService;
        this.objectMapper = objectMapper;
        router.register(this::receive);
    }

    private void receive(AuthenticatedMqttMessage message) {
        try {
            if (message.topic().endsWith("/presence")) receivePresence(message);
            else if (message.topic().endsWith("/status")) receiveStatus(message);
        } catch (RuntimeException exception) {
            LOGGER.warn("Rejected MQTT state from machine {} on topic {}: {}",
                    message.machine().deviceCode(), message.topic(), exception.getMessage());
        }
    }

    private void receivePresence(AuthenticatedMqttMessage message) {
        Map<String, Object> value = json(message.payload());
        requireSchema(value);
        DeviceMqttPresence presence = enumValue(DeviceMqttPresence.class, text(value, "state"));
        text(value, "reason");
        stateService.updatePresence(message.machine(), presence,
                dateTime(text(value, "changedAt")));
    }

    private void receiveStatus(AuthenticatedMqttMessage message) {
        Map<String, Object> envelope = json(message.payload());
        requireSchema(envelope);
        if (!"device.status-reported".equals(text(envelope, "type"))) {
            throw new IllegalArgumentException("Unexpected MQTT status type");
        }
        if (!message.machine().deviceCode().equals(text(envelope, "deviceCode"))) {
            throw new IllegalArgumentException("MQTT status identity does not match");
        }
        Map<String, Object> payload = object(envelope, "payload");
        DeviceOperationalState state = enumValue(DeviceOperationalState.class,
                text(payload, "operationalState"));
        long uptime = nonNegativeLong(payload, "uptimeSeconds");
        stateService.updateOperationalState(message.machine(), state,
                text(payload, "serviceMode"), text(payload, "softwareVersion"), uptime,
                dateTime(text(envelope, "occurredAt")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, Map.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Malformed MQTT state payload", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Missing " + field);
        return (Map<String, Object>) value;
    }

    private void requireSchema(Map<String, Object> value) {
        Object version = value.get("schemaVersion");
        if (!(version instanceof Number number) || number.intValue() != 1) {
            throw new IllegalArgumentException("Unsupported MQTT state schema");
        }
    }

    private String text(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Missing " + field);
        return text.trim();
    }

    private long nonNegativeLong(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof Number number) || number.longValue() < 0) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return number.longValue();
    }

    private LocalDateTime dateTime(String value) {
        return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown MQTT device state", exception);
        }
    }
}
