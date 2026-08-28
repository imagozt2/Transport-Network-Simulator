package com.transport.simulator.mqtt;

import com.transport.simulator.service.model.TicketValidationDecision;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MqttTicketValidationResponsePublisher {

    private final ControlCenterMqttClient mqttClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MqttTicketValidationResponsePublisher(ControlCenterMqttClient mqttClient,
            ObjectMapper objectMapper, Clock clock) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void publish(AuthenticatedMqttMachine machine, String requestMessageId,
            TicketValidationDecision decision) {
        String now = clock.instant().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("validationReference", decision.validationReference());
        payload.put("decision", decision.decision().name());
        payload.put("reasonCode", decision.reasonCode());
        payload.put("ticketCode", decision.ticketCode());
        payload.put("validAtStationCode", decision.validAtStationCode());
        payload.put("fareAmount", decision.fareAmount());
        payload.put("remainingBalance", decision.remainingBalance());
        payload.put("consumedTrips", decision.consumedTrips());
        payload.put("remainingTrips", decision.remainingTrips());
        payload.put("validFrom", instant(decision.validFrom()));
        payload.put("validUntil", instant(decision.validUntil()));
        payload.put("decidedAt", decision.decidedAt().atZone(clock.getZone()).toInstant().toString());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", 1);
        envelope.put("messageId", UUID.randomUUID().toString());
        envelope.put("correlationId", requestMessageId);
        envelope.put("type", "ticket.validation-decided");
        envelope.put("deviceCode", machine.deviceCode());
        envelope.put("occurredAt", now);
        envelope.put("sentAt", now);
        envelope.put("payload", payload);
        try {
            mqttClient.publish("rmm/v1/devices/" + machine.deviceCode() + "/responses",
                    objectMapper.writeValueAsBytes(envelope), 1, false);
        } catch (Exception exception) {
            throw new MqttTransportException("Ticket validation decision could not be published", exception);
        }
    }

    private String instant(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(clock.getZone()).toInstant().toString();
    }
}
