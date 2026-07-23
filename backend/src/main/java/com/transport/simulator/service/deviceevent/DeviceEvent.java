package com.transport.simulator.service.deviceevent;

import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.enums.OperationalEventType;
import java.time.LocalDateTime;
import java.util.Objects;

public record DeviceEvent(
        String deviceCode,
        LogOrigin origin,
        OperationalEventType type,
        LogSeverity severity,
        String message,
        LocalDateTime occurredAt,
        String externalReference,
        String payloadJson
) {

    public DeviceEvent {
        deviceCode = requireText(deviceCode, "deviceCode");
        origin = Objects.requireNonNull(origin, "origin is required");
        type = Objects.requireNonNull(type, "type is required");
        severity = Objects.requireNonNull(severity, "severity is required");
        message = requireText(message, "message");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");

        if (deviceCode.length() > 50) {
            throw new IllegalArgumentException("deviceCode cannot exceed 50 characters");
        }
        if (message.length() > 500) {
            throw new IllegalArgumentException("message cannot exceed 500 characters");
        }
        if (externalReference != null && externalReference.length() > 150) {
            throw new IllegalArgumentException("externalReference cannot exceed 150 characters");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
