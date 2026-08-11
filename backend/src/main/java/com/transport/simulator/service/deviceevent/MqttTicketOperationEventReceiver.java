package com.transport.simulator.service.deviceevent;

import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.TicketQrValidationType;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.mqtt.AuthenticatedMqttMachine;
import com.transport.simulator.mqtt.AuthenticatedMqttMessage;
import com.transport.simulator.mqtt.AuthenticatedMqttMessageRouter;
import com.transport.simulator.mqtt.MqttTicketValidationResponsePublisher;
import com.transport.simulator.service.TicketMachinePurchaseService;
import com.transport.simulator.service.TicketValidationService;
import com.transport.simulator.service.model.TicketMachinePurchaseRequest;
import com.transport.simulator.service.model.TicketValidationRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MqttTicketOperationEventReceiver {
    private static final Set<DeviceEventType> SALE_AND_VALIDATION_EVENTS = EnumSet.of(
            DeviceEventType.TICKET_PURCHASE_REQUESTED,
            DeviceEventType.TICKET_PURCHASE_COMPLETED,
            DeviceEventType.TICKET_PURCHASE_FAILED,
            DeviceEventType.QR_TICKET_GENERATED,
            DeviceEventType.VALIDATION_REQUESTED,
            DeviceEventType.VALIDATION_ACCEPTED,
            DeviceEventType.VALIDATION_REJECTED,
            DeviceEventType.VALIDATION_FAILED
    );

    private final DeviceEventIngress ingress;
    private final ObjectMapper objectMapper;
    private final TicketMachinePurchaseService purchaseService;
    private final TicketValidationService validationService;
    private final MqttTicketValidationResponsePublisher validationPublisher;

    public MqttTicketOperationEventReceiver(AuthenticatedMqttMessageRouter router,
            DeviceEventIngress ingress, ObjectMapper objectMapper,
            TicketMachinePurchaseService purchaseService,
            TicketValidationService validationService,
            MqttTicketValidationResponsePublisher validationPublisher) {
        this.ingress = ingress;
        this.objectMapper = objectMapper;
        this.purchaseService = purchaseService;
        this.validationService = validationService;
        this.validationPublisher = validationPublisher;
        router.register(this::receive);
    }

    private void receive(AuthenticatedMqttMessage authenticated) {
        if (authenticated.topic().endsWith("/requests/validations")) {
            receiveValidationRequest(authenticated);
        } else if (authenticated.topic().endsWith("/requests/purchases")) {
            receivePurchaseRequest(authenticated);
        } else if (authenticated.topic().contains("/events/operation")) {
            receiveOperationEvent(authenticated);
        }
    }

    private void receivePurchaseRequest(AuthenticatedMqttMessage authenticated) {
        Map<String, Object> envelope = envelope(authenticated.payload());
        verifyEnvelope(envelope, authenticated.machine());
        if (!"ticket.purchase-requested".equals(text(envelope, "type"))) {
            throw new IllegalArgumentException("Unexpected purchase message type");
        }
        if (authenticated.machine().deviceType() != DeviceType.TICKET_MACHINE) {
            throw new IllegalArgumentException("Only a ticket machine can request a purchase");
        }
        Map<String, Object> payload = object(envelope, "payload");
        String reference = text(payload, "purchaseReference");
        UUID.fromString(reference);
        String productCode = text(payload, "productCode");
        if (!"SIMULATED".equals(text(payload, "paymentMethod"))) {
            throw new IllegalArgumentException("Ticket machines only support simulated payments");
        }
        if (!"EUR".equals(text(payload, "currency"))) {
            throw new IllegalArgumentException("Unsupported purchase currency");
        }
        Object paidAmount = payload.get("paidAmount");
        if (!(paidAmount instanceof Number number) || number.doubleValue() <= 0) {
            throw new IllegalArgumentException("A positive paidAmount is required");
        }
        Map<String, Object> configuration = object(payload, "configuration");
        if (configuration.isEmpty()) {
            throw new IllegalArgumentException("Ticket configuration is required");
        }

        Map<String, Object> safePayload = new LinkedHashMap<>();
        safePayload.put("purchaseReference", reference);
        safePayload.put("productCode", productCode);
        safePayload.put("paymentMethod", "SIMULATED");
        safePayload.put("paidAmount", number.doubleValue());
        safePayload.put("currency", "EUR");
        safePayload.put("configuration", new LinkedHashMap<>(configuration));
        ingress.receive(message(envelope, authenticated.machine(),
                DeviceEventType.TICKET_PURCHASE_REQUESTED, LogSeverity.INFO,
                "Solicitud de emisión de billete recibida", safePayload));
        purchaseService.purchase(authenticated.machine().deviceId(), new TicketMachinePurchaseRequest(
                reference,
                productCode,
                nullableText(configuration, "originStationCode"),
                nullableText(configuration, "destinationStationCode"),
                nullableInteger(configuration, "quantity"),
                nullableDecimal(configuration, "rechargeAmount"),
                BigDecimal.valueOf(number.doubleValue())
        ));
    }

    private void receiveOperationEvent(AuthenticatedMqttMessage authenticated) {
        Map<String, Object> envelope = envelope(authenticated.payload());
        verifyEnvelope(envelope, authenticated.machine());
        Map<String, Object> payload = object(envelope, "payload");
        DeviceEventType type = enumValue(DeviceEventType.class, text(payload, "eventCode"), "eventCode");
        if (!SALE_AND_VALIDATION_EVENTS.contains(type)) {
            return;
        }
        requireCompatibleMachine(authenticated.machine(), type);
        LogSeverity severity = enumValue(LogSeverity.class,
                optionalText(payload, "severity", "INFO"), "severity");
        Map<String, Object> details = optionalObject(payload, "details");
        ingress.receive(message(envelope, authenticated.machine(), type, severity,
                description(type), details));
    }

    private void receiveValidationRequest(AuthenticatedMqttMessage authenticated) {
        Map<String, Object> envelope = envelope(authenticated.payload());
        verifyEnvelope(envelope, authenticated.machine());
        if (!"ticket.validation-requested".equals(text(envelope, "type"))) {
            throw new IllegalArgumentException("Unexpected validation message type");
        }
        Map<String, Object> payload = object(envelope, "payload");
        String direction = text(payload, "direction");
        DeviceType requiredType = switch (direction) {
            case "ENTRY" -> DeviceType.ENTRY_VALIDATOR;
            case "EXIT" -> DeviceType.EXIT_VALIDATOR;
            default -> throw new IllegalArgumentException("Unknown validation direction");
        };
        if (authenticated.machine().deviceType() != requiredType) {
            throw new IllegalArgumentException("Validation direction is incompatible with the machine");
        }
        if (!authenticated.machine().stationCode().equals(text(payload, "stationCode"))) {
            throw new IllegalArgumentException("Validation station is incompatible with the machine");
        }
        UUID.fromString(text(payload, "validationReference"));
        text(payload, "qrValue");

        Map<String, Object> safePayload = new LinkedHashMap<>();
        safePayload.put("validationReference", payload.get("validationReference"));
        safePayload.put("direction", direction);
        safePayload.put("stationCode", payload.get("stationCode"));
        ingress.receive(message(envelope, authenticated.machine(),
                DeviceEventType.VALIDATION_REQUESTED, LogSeverity.INFO,
                "Solicitud de validación de billete recibida", safePayload));
        var decision = validationService.validate(authenticated.machine().deviceId(),
                new TicketValidationRequest(
                        text(payload, "validationReference"),
                        TicketQrValidationType.valueOf(direction),
                        text(payload, "stationCode"),
                        text(payload, "qrValue")
                ));
        validationPublisher.publish(authenticated.machine(), text(envelope, "messageId"), decision);
    }

    private DeviceEventMessage message(Map<String, Object> envelope,
            AuthenticatedMqttMachine machine, DeviceEventType type, LogSeverity severity,
            String description, Map<String, Object> payload) {
        String messageId = text(envelope, "messageId");
        UUID.fromString(messageId);
        return new DeviceEventMessage(DeviceEventMessage.CURRENT_SCHEMA_VERSION,
                messageId, machine.deviceCode(), type, severity, description,
                Instant.parse(text(envelope, "occurredAt")), payload);
    }

    private void verifyEnvelope(Map<String, Object> envelope, AuthenticatedMqttMachine machine) {
        Object version = envelope.get("schemaVersion");
        if (!(version instanceof Number number) || number.intValue() != 1) {
            throw new IllegalArgumentException("Unsupported MQTT schema version");
        }
        if (!machine.deviceCode().equals(text(envelope, "deviceCode"))) {
            throw new IllegalArgumentException("Authenticated machine does not match the envelope");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> envelope(byte[] payload) {
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Malformed MQTT JSON", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Missing " + field);
        return (Map<String, Object>) value;
    }

    private Map<String, Object> optionalObject(Map<String, Object> source, String field) {
        Object value = source.get(field);
        return value instanceof Map<?, ?> ? object(source, field) : Map.of();
    }

    private String text(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return text.trim();
    }

    private String optionalText(Map<String, Object> source, String field, String fallback) {
        Object value = source.get(field);
        return value instanceof String text && !text.isBlank() ? text.trim() : fallback;
    }

    private String nullableText(Map<String, Object> source, String field) {
        Object value = source.get(field);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private Integer nullableInteger(Map<String, Object> source, String field) {
        Object value = source.get(field);
        return value instanceof Number number ? number.intValue() : null;
    }

    private BigDecimal nullableDecimal(Map<String, Object> source, String field) {
        Object value = source.get(field);
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : null;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + field, exception);
        }
    }

    private String description(DeviceEventType type) {
        return switch (type) {
            case TICKET_PURCHASE_REQUESTED -> "Venta de título solicitada";
            case TICKET_PURCHASE_COMPLETED -> "Venta de título completada";
            case TICKET_PURCHASE_FAILED -> "Venta de título rechazada";
            case QR_TICKET_GENERATED -> "Código QR de billete generado";
            case VALIDATION_REQUESTED -> "Validación de billete solicitada";
            case VALIDATION_ACCEPTED -> "Validación de billete aceptada";
            case VALIDATION_REJECTED -> "Validación de billete rechazada";
            case VALIDATION_FAILED -> "No se pudo completar la validación";
            default -> throw new IllegalArgumentException("Unsupported ticket operation event");
        };
    }

    private void requireCompatibleMachine(AuthenticatedMqttMachine machine, DeviceEventType type) {
        boolean purchase = type == DeviceEventType.TICKET_PURCHASE_REQUESTED
                || type == DeviceEventType.TICKET_PURCHASE_COMPLETED
                || type == DeviceEventType.TICKET_PURCHASE_FAILED;
        purchase = purchase || type == DeviceEventType.QR_TICKET_GENERATED;
        if (purchase && machine.deviceType() != DeviceType.TICKET_MACHINE) {
            throw new IllegalArgumentException("A validator cannot report ticket sales");
        }
        if (!purchase && machine.deviceType() == DeviceType.TICKET_MACHINE) {
            throw new IllegalArgumentException("A ticket machine cannot report validations");
        }
    }
}
