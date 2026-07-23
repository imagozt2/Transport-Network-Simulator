package com.transport.simulator.dto.response.operationallog;

import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import java.time.LocalDateTime;

public record OperationalLogResponse(
        Long id,
        LogOrigin origin,
        DeviceEventType eventType,
        LogSeverity severity,
        String message,
        Long deviceId,
        String deviceCode,
        String deviceName,
        Long stationId,
        String stationCode,
        String stationName,
        String externalReference,
        LocalDateTime occurredAt,
        LocalDateTime receivedAt
) {

    public static OperationalLogResponse from(DeviceEventLog eventLog) {
        return new OperationalLogResponse(
                eventLog.getId(),
                eventLog.getOrigin(),
                eventLog.getEventType(),
                eventLog.getSeverity(),
                eventLog.getMessage(),
                eventLog.getDevice().getId(),
                eventLog.getDevice().getCode(),
                eventLog.getDevice().getName(),
                eventLog.getStation().getId(),
                eventLog.getStation().getCode(),
                eventLog.getStation().getName(),
                eventLog.getExternalReference(),
                eventLog.getOccurredAt(),
                eventLog.getReceivedAt()
        );
    }
}
