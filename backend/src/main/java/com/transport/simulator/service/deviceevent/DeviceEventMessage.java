package com.transport.simulator.service.deviceevent;

import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.LogSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public record DeviceEventMessage(
        @NotBlank
        @Size(max = 20)
        String schemaVersion,

        @NotBlank
        @Size(max = 150)
        String eventId,

        @NotBlank
        @Size(max = 50)
        String deviceCode,

        @NotNull
        DeviceEventType type,

        @NotNull
        LogSeverity severity,

        @NotBlank
        @Size(max = 500)
        String message,

        @NotNull
        Instant occurredAt,

        Map<String, Object> payload
) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public DeviceEventMessage {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
