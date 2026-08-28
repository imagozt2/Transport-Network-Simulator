package com.transport.simulator.service.deviceevent;

import com.transport.simulator.enums.DeviceMqttPresence;
import com.transport.simulator.enums.DeviceEventSource;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceOperationalState;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.mqtt.AuthenticatedMqttMessage;
import com.transport.simulator.mqtt.AuthenticatedMqttMessageRouter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MqttDeviceStateReceiver {
    private final MqttDeviceStateService stateService;
    private final DeviceEventRegistrationService eventRegistrationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MqttDeviceStateReceiver(AuthenticatedMqttMessageRouter router,
            MqttDeviceStateService stateService,
            DeviceEventRegistrationService eventRegistrationService,
            ObjectMapper objectMapper, Clock clock) {
        this.stateService = stateService;
        this.eventRegistrationService = eventRegistrationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        router.register(this::receive);
    }

    private void receive(AuthenticatedMqttMessage message) {
        if (message.topic().endsWith("/presence")) receivePresence(message);
        else if (message.topic().endsWith("/status")) receiveStatus(message);
    }

    private void receivePresence(AuthenticatedMqttMessage message) {
        Map<String, Object> value = json(message.payload());
        requireSchema(value);
        DeviceMqttPresence presence = enumValue(DeviceMqttPresence.class, text(value, "state"));
        String reason = text(value, "reason");
        dateTime(text(value, "changedAt"));
        LocalDateTime receivedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (stateService.updatePresence(message.machine(), presence, receivedAt)) {
            eventRegistrationService.register(new DeviceEvent(
                    message.machine().deviceCode(), LogOrigin.MQTT, DeviceEventSource.REAL,
                    presence == DeviceMqttPresence.ONLINE
                            ? DeviceEventType.DEVICE_ONLINE : DeviceEventType.DEVICE_OFFLINE,
                    LogSeverity.INFO,
                    presence == DeviceMqttPresence.ONLINE
                            ? "Máquina conectada mediante MQTT: " + reason
                            : "Máquina desconectada de MQTT: " + reason,
                    receivedAt, null, payload(message)
            ));
        }
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
        LocalDateTime occurredAt = dateTime(text(envelope, "occurredAt"));
        if (stateService.updateOperationalState(message.machine(), state,
                text(payload, "serviceMode"), text(payload, "softwareVersion"), uptime,
                occurredAt)) {
            eventRegistrationService.register(new DeviceEvent(
                    message.machine().deviceCode(), LogOrigin.MQTT, DeviceEventSource.REAL,
                    DeviceEventType.DEVICE_STATUS_CHANGED, LogSeverity.INFO,
                    "Estado MQTT actualizado: " + state,
                    occurredAt, optionalText(envelope, "messageId"), payload(message)
            ));
        }
    }

    private String payload(AuthenticatedMqttMessage message) {
        return new String(message.payload(), StandardCharsets.UTF_8);
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

    private String optionalText(Map<String, Object> source, String field) {
        Object value = source.get(field);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
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
