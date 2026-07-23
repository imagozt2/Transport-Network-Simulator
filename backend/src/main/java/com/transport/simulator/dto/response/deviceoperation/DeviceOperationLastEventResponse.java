package com.transport.simulator.dto.response.deviceoperation;

import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import java.time.LocalDateTime;

public record DeviceOperationLastEventResponse(
        Long id,
        DeviceEventType type,
        LogSeverity severity,
        String message,
        LogOrigin origin,
        LocalDateTime occurredAt
) {
}
