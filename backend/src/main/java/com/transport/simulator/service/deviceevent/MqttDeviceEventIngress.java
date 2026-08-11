package com.transport.simulator.service.deviceevent;

import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.DeviceEventSource;
import com.transport.simulator.repository.DeviceEventLogRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
class MqttDeviceEventIngress implements DeviceEventIngress {

    private final DeviceEventLogRepository eventLogRepository;
    private final DeviceEventRegistrationService registrationService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;

    MqttDeviceEventIngress(
            DeviceEventLogRepository eventLogRepository,
            DeviceEventRegistrationService registrationService,
            ObjectMapper objectMapper,
            Validator validator,
            Clock clock
    ) {
        this.eventLogRepository = eventLogRepository;
        this.registrationService = registrationService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clock = clock;
    }

    @Override
    public DeviceEventReceipt receive(DeviceEventMessage message) {
        var violations = validator.validate(message);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        if (!DeviceEventMessage.CURRENT_SCHEMA_VERSION.equals(message.schemaVersion())) {
            throw new UnsupportedDeviceEventSchemaException(message.schemaVersion());
        }

        return eventLogRepository
                .findByOriginAndExternalReference(LogOrigin.MQTT, message.eventId())
                .map(log -> duplicateReceipt(message, log))
                .orElseGet(() -> register(message));
    }

    private DeviceEventReceipt register(DeviceEventMessage message) {
        DeviceEvent event = new DeviceEvent(
                message.deviceCode(),
                LogOrigin.MQTT,
                DeviceEventSource.REAL,
                message.type(),
                message.severity(),
                message.message(),
                LocalDateTime.ofInstant(message.occurredAt(), clock.getZone()),
                message.eventId(),
                serializePayload(message)
        );

        DeviceEventLog log = registrationService.register(event);
        return new DeviceEventReceipt(
                message.eventId(),
                log.getId(),
                DeviceEventReceipt.Status.ACCEPTED
        );
    }

    private DeviceEventReceipt duplicateReceipt(
            DeviceEventMessage message,
            DeviceEventLog log
    ) {
        return new DeviceEventReceipt(
                message.eventId(),
                log.getId(),
                DeviceEventReceipt.Status.DUPLICATE
        );
    }

    private String serializePayload(DeviceEventMessage message) {
        try {
            return objectMapper.writeValueAsString(message.payload());
        } catch (JacksonException exception) {
            throw new InvalidDeviceEventPayloadException(message.eventId(), exception);
        }
    }
}
