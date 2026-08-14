package com.transport.simulator.mqtt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AuthenticatedMqttMessageRouter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatedMqttMessageRouter.class);
    private final MqttMachineAuthenticationService authenticationService;
    private final ObjectMapper objectMapper;
    private final MqttInboundIdempotencyService idempotencyService;
    private final List<Consumer<AuthenticatedMqttMessage>> consumers = new CopyOnWriteArrayList<>();

    public AuthenticatedMqttMessageRouter(ControlCenterMqttClient mqttClient,
            MqttMachineAuthenticationService authenticationService, ObjectMapper objectMapper,
            MqttInboundIdempotencyService idempotencyService) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        mqttClient.subscribe("rmm/v1/devices/+/presence", 1, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/status", 1, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/telemetry", 0, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/events/+", 1, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/requests/validations", 1, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/requests/purchases", 1, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/requests/recharges", 1, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/acks", 1, this::route);
    }

    public void register(Consumer<AuthenticatedMqttMessage> consumer) {
        if (consumer == null) throw new IllegalArgumentException("Authenticated MQTT consumer is required");
        consumers.add(consumer);
    }

    private void route(String topic, byte[] payload) {
        Map<String, Object> message;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(payload, Map.class);
            message = parsed;
        } catch (Exception exception) {
            LOGGER.warn("Invalid MQTT machine message on topic {}", topic, exception);
            return;
        }

        AuthenticatedMqttMachine machine;
        try {
            Object deviceCode = message.get("deviceCode");
            machine = topic.endsWith("/presence")
                    ? authenticationService.authenticatePresence(topic)
                    : authenticationService.authenticate(
                            topic, deviceCode instanceof String value ? value : null);
        } catch (MqttMachineAuthenticationException exception) {
            LOGGER.warn("Rejected MQTT machine message on topic {}: {}", topic,
                    exception.getMessage());
            return;
        }

        AuthenticatedMqttMessage authenticated = new AuthenticatedMqttMessage(machine, topic, payload);
        if (topic.endsWith("/presence")) {
            dispatchPresence(authenticated);
            return;
        }

        String messageId;
        try {
            Object rawMessageId = message.get("messageId");
            if (!(rawMessageId instanceof String value)) throw new IllegalArgumentException("Missing messageId");
            messageId = UUID.fromString(value.trim()).toString();
            MqttIdempotencyClaim claim = idempotencyService.claim(
                    messageId, machine.deviceId(), topic, payload);
            if (claim == MqttIdempotencyClaim.DUPLICATE) return;
        } catch (MqttMessageIdReuseException exception) {
            LOGGER.warn("Rejected MQTT messageId reuse from machine {} on topic {}",
                    machine.deviceCode(), topic);
            return;
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Rejected MQTT message without a valid messageId from machine {} on topic {}",
                    machine.deviceCode(), topic);
            return;
        }

        try {
            consumers.forEach(consumer -> consumer.accept(authenticated));
            idempotencyService.complete(messageId);
        } catch (IllegalArgumentException exception) {
            idempotencyService.reject(messageId, exception.getMessage());
            LOGGER.warn("Rejected invalid MQTT message {} from machine {}: {}",
                    messageId, machine.deviceCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            idempotencyService.fail(messageId, exception.getMessage());
            throw exception;
        }
    }

    private void dispatchPresence(AuthenticatedMqttMessage authenticated) {
        try {
            consumers.forEach(consumer -> consumer.accept(authenticated));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Rejected invalid MQTT presence from machine {}: {}",
                    authenticated.machine().deviceCode(), exception.getMessage());
        }
    }
}
