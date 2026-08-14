package com.transport.simulator.dto.response.operationallog;

import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceEventSource;
import com.transport.simulator.enums.CompensatoryDeliveryMethod;
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
        String productCode,
        TicketProductType ticketType,
        CompensatoryDeliveryMethod deliveryMethod,
        Boolean simulated,
        String compensatoryIssuanceCode,
        String externalReference,
        LocalDateTime occurredAt,
        LocalDateTime receivedAt
) {

    public static OperationalLogResponse from(DeviceEventLog eventLog) {
        var device = eventLog.getDevice();
        var station = eventLog.getStation();
        var issuance = eventLog.getCompensatoryIssuance();
        return new OperationalLogResponse(
                eventLog.getId(),
                eventLog.getOrigin(),
                eventLog.getSource(),
                eventLog.getEventType(),
                eventLog.getSeverity(),
                eventLog.getMessage(),
                device == null ? null : device.getId(),
                device == null ? null : device.getCode(),
                device == null ? null : device.getName(),
                station == null ? null : station.getId(),
                station == null ? null : station.getCode(),
                station == null ? null : station.getName(),
                eventLog.getTicket() == null ? null : eventLog.getTicket().getCode(),
                issuance == null ? null : issuance.getProduct().getCode(),
                issuance == null ? null : issuance.getProduct().getProductType(),
                issuance == null ? null : issuance.getDeliveryMethod(),
                issuance == null ? null
                        : issuance.getDeliveryMethod() == CompensatoryDeliveryMethod.PHYSICAL_DEVICE
                                && issuance.getIssuedTicket() == null,
                issuance == null ? null : issuance.getCode(),
                eventLog.getExternalReference(),
                eventLog.getOccurredAt(),
                eventLog.getReceivedAt()
        );
    }
}
