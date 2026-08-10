package com.transport.simulator.mqtt;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.DeviceMqttCommand;
import com.transport.simulator.entity.DeviceMqttIdentity;
import com.transport.simulator.enums.DeviceMqttCommandType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.repository.DeviceMqttCommandRepository;
import com.transport.simulator.repository.DeviceMqttIdentityRepository;
import com.transport.simulator.repository.DeviceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class MqttDeviceCommandService {
    private final DeviceRepository deviceRepository;
    private final DeviceMqttIdentityRepository identityRepository;
    private final DeviceMqttCommandRepository commandRepository;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MqttDeviceCommandService(DeviceRepository deviceRepository,
            DeviceMqttIdentityRepository identityRepository,
            DeviceMqttCommandRepository commandRepository,
            ApplicationEventPublisher events, ObjectMapper objectMapper, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.identityRepository = identityRepository;
        this.commandRepository = commandRepository;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public DeviceMqttCommand send(String deviceCode, DeviceMqttCommandType type,
            Map<String, Object> payload, Duration validity) {
        Device device = deviceRepository.findByCodeAndActiveTrue(normalize(deviceCode))
                .orElseThrow(() -> new IllegalArgumentException("Unknown target machine"));
        LocalDateTime now = LocalDateTime.now(clock);
        DeviceMqttIdentity identity = identityRepository.findByDeviceId(device.getId())
                .filter(candidate -> candidate.canAuthenticate(now))
                .orElseThrow(() -> new IllegalArgumentException("Target machine has no active MQTT identity"));
        if (!identity.getMqttClientId().equals(device.getCode())) {
            throw new IllegalArgumentException("Target machine MQTT identity is inconsistent");
        }
        if (type == DeviceMqttCommandType.TICKET_ISSUE
                && device.getType() != DeviceType.TICKET_MACHINE) {
            throw new IllegalArgumentException("Ticket issue commands require a ticket machine");
        }
        if (validity == null || validity.isZero() || validity.isNegative()) {
            throw new IllegalArgumentException("Command validity must be positive");
        }
        Map<String, Object> safePayload = payload == null
                ? Map.of() : new LinkedHashMap<>(payload);
        DeviceMqttCommand command = commandRepository.save(new DeviceMqttCommand(
                "RMM-CMD-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT),
                UUID.randomUUID().toString(), device, type, json(safePayload),
                now, now.plus(validity)));
        events.publishEvent(new DeviceMqttCommandCreated(command.getId()));
        return command;
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Command payload is not serializable", exception);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Target machine is required");
        return value.trim();
    }
}
