package com.transport.simulator.mqtt;

import com.transport.simulator.enums.DeviceMqttCommandStatus;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MqttDeviceCommandAcknowledgementReceiver {
    private final MqttDeviceCommandAcknowledgementService acknowledgementService;
    private final ObjectMapper objectMapper;

    public MqttDeviceCommandAcknowledgementReceiver(
            AuthenticatedMqttMessageRouter router,
            MqttDeviceCommandAcknowledgementService acknowledgementService,
            ObjectMapper objectMapper
    ) {
        this.acknowledgementService = acknowledgementService;
        this.objectMapper = objectMapper;
        router.register(this::receive);
    }

    void receive(AuthenticatedMqttMessage authenticated) {
        if (!authenticated.topic().endsWith("/acks")) {
            return;
        }
        Map<String, Object> envelope = json(authenticated.payload());
        if (!"ticket.issue-acknowledged".equals(text(envelope, "type"))) {
            throw new IllegalArgumentException("Unexpected command acknowledgement type");
        }
        if (!authenticated.machine().deviceCode().equals(text(envelope, "deviceCode"))) {
            throw new IllegalArgumentException("Command acknowledgement identity mismatch");
        }
        Map<String, Object> payload = object(envelope, "payload");
        String commandId = text(payload, "commandId");
        DeviceMqttCommandStatus status = status(text(payload, "status"));
        String resultCode = text(payload, "resultCode");
        String issuanceCode = optionalText(payload, "issuanceCode");
        acknowledgementService.acknowledge(
                authenticated.machine().deviceId(), commandId, issuanceCode, status, resultCode);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(byte[] value) {
        try { return objectMapper.readValue(value, Map.class); }
        catch (Exception exception) { throw new IllegalArgumentException("Malformed acknowledgement JSON", exception); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Missing " + field);
        return (Map<String, Object>) value;
    }

    private String text(Map<String, Object> source, String field) {
        String value = optionalText(source, field);
        if (value == null) throw new IllegalArgumentException("Missing " + field);
        return value;
    }

    private String optionalText(Map<String, Object> source, String field) {
        Object value = source.get(field);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private DeviceMqttCommandStatus status(String value) {
        try { return DeviceMqttCommandStatus.valueOf(value); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown acknowledgement status", exception);
        }
    }
}
