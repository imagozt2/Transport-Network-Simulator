package com.transport.simulator.mqtt;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.service.model.TicketMachineRechargeResult;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MqttTicketRechargeResponsePublisher {

    private final ControlCenterMqttClient mqttClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MqttTicketRechargeResponsePublisher(
            ControlCenterMqttClient mqttClient,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void publish(
            AuthenticatedMqttMachine machine,
            String requestMessageId,
            TicketMachineRechargeResult result
    ) {
        Ticket ticket = result.ticket();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rechargeReference", result.purchase().getExternalReference());
        payload.put("rechargeCode", result.purchase().getCode());
        payload.put("status", result.purchase().getStatus().name());
        payload.put("ticketCode", ticket.getCode());
        payload.put("productType", ticket.getProductType().name());
        payload.put("ticketStatus", ticket.getStatus().name());
        payload.put("totalAmount", result.purchase().getTotalAmount());
        payload.put("currency", result.purchase().getCurrency());
        payload.put("remainingTrips", ticket.getRemainingTrips());
        payload.put("validFrom", instant(ticket.getValidFrom()));
        payload.put("validUntil", instant(ticket.getValidUntil()));
        payload.put("balanceAmount", ticket.getBalanceAmount());
        payload.put("qrValue", result.qrValue());

        String now = clock.instant().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("schemaVersion", 1);
        envelope.put("messageId", UUID.randomUUID().toString());
        envelope.put("correlationId", requestMessageId);
        envelope.put("type", "ticket.recharge-completed");
        envelope.put("deviceCode", machine.deviceCode());
        envelope.put("occurredAt", now);
        envelope.put("sentAt", now);
        envelope.put("payload", payload);

        try {
            mqttClient.publish(
                    "rmm/v1/devices/" + machine.deviceCode() + "/responses",
                    objectMapper.writeValueAsBytes(envelope), 1, false
            );
        } catch (Exception exception) {
            throw new MqttTransportException("Ticket recharge response could not be published", exception);
        }
    }

    private String instant(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(clock.getZone()).toInstant().toString();
    }
}
