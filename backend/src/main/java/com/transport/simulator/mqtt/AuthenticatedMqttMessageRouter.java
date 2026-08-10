package com.transport.simulator.mqtt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AuthenticatedMqttMessageRouter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatedMqttMessageRouter.class);
    private final MqttMachineAuthenticationService authenticationService;
    private final ObjectMapper objectMapper;
    private final List<Consumer<AuthenticatedMqttMessage>> consumers = new CopyOnWriteArrayList<>();

    public AuthenticatedMqttMessageRouter(ControlCenterMqttClient mqttClient,
            MqttMachineAuthenticationService authenticationService, ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
        mqttClient.subscribe("rmm/v1/devices/+/presence", 1, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/status", 1, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/telemetry", 0, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/events/+", 1, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/requests/validations", 1, this::route);
        mqttClient.subscribe("rmm/v1/devices/+/acks", 1, this::route);
    }

    public void register(Consumer<AuthenticatedMqttMessage> consumer) {
        if (consumer == null) throw new IllegalArgumentException("Authenticated MQTT consumer is required");
        consumers.add(consumer);
    }

    private void route(String topic, byte[] payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> message = objectMapper.readValue(payload, Map.class);
            Object deviceCode = message.get("deviceCode");
            AuthenticatedMqttMachine machine = topic.endsWith("/presence")
                    ? authenticationService.authenticatePresence(topic)
                    : authenticationService.authenticate(
                            topic, deviceCode instanceof String value ? value : null);
            AuthenticatedMqttMessage authenticated = new AuthenticatedMqttMessage(
                    machine, topic, payload);
            consumers.forEach(consumer -> consumer.accept(authenticated));
        } catch (MqttMachineAuthenticationException exception) {
            LOGGER.warn("Rejected MQTT machine message on topic {}: {}", topic,
                    exception.getMessage());
        } catch (Exception exception) {
            LOGGER.warn("Invalid MQTT machine message on topic {}", topic, exception);
        }
    }
}
