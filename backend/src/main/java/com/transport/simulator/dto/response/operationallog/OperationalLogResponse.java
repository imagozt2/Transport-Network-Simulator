package com.transport.simulator.dto.response.operationallog;

import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceEventSource;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.enums.TicketProductType;
import java.time.LocalDateTime;

public record OperationalLogResponse(
        Long id,
        LogOrigin origin,
        DeviceEventSource source,
        DeviceEventType eventType,
        LogSeverity severity,
        String message,
        Long deviceId,
        String deviceCode,
        String deviceName,
        Long stationId,
        String stationCode,
        String stationName,
        String ticketCode,
        TicketProductType ticketType,
        String compensatoryIssuanceCode,
        String externalReference,
        LocalDateTime occurredAt,
        LocalDateTime receivedAt
) {

    public static OperationalLogResponse from(DeviceEventLog eventLog) {
        return new OperationalLogResponse(
                eventLog.getId(),
                eventLog.getOrigin(),
                eventLog.getSource(),
                eventLog.getEventType(),
                eventLog.getSeverity(),
                eventLog.getMessage(),
                eventLog.getDevice().getId(),
                eventLog.getDevice().getCode(),
                eventLog.getDevice().getName(),
                eventLog.getStation().getId(),
                eventLog.getStation().getCode(),
                eventLog.getStation().getName(),
                eventLog.getTicket() == null ? null : eventLog.getTicket().getCode(),
                eventLog.getCompensatoryIssuance() == null
                        ? null : eventLog.getCompensatoryIssuance().getProduct().getProductType(),
                eventLog.getCompensatoryIssuance() == null
                        ? null : eventLog.getCompensatoryIssuance().getCode(),
                eventLog.getExternalReference(),
                eventLog.getOccurredAt(),
                eventLog.getReceivedAt()
        );
    }
}
