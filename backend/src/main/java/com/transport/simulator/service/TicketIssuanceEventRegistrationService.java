package com.transport.simulator.service;

import com.transport.simulator.entity.CompensatoryTicketIssuance;
import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceEventSource;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.repository.DeviceEventLogRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TicketIssuanceEventRegistrationService {

    private final DeviceEventLogRepository logRepository;
    private final ObjectMapper objectMapper;

    public TicketIssuanceEventRegistrationService(
            DeviceEventLogRepository logRepository,
            ObjectMapper objectMapper
    ) {
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    public DeviceEventLog registerRequested(
            CompensatoryTicketIssuance issuance,
            LocalDateTime occurredAt
    ) {
        return register(
                issuance,
                null,
                DeviceEventType.COMPENSATORY_TICKET_ISSUANCE_REQUESTED,
                "Solicitud de emisión compensatoria de "
                        + issuance.getProduct().getProductType(),
                "REQUESTED",
                occurredAt
        );
    }

    public DeviceEventLog registerCompleted(
            CompensatoryTicketIssuance issuance,
            LocalDateTime occurredAt
    ) {
        return register(
                issuance,
                issuance.getIssuedTicket(),
                DeviceEventType.COMPENSATORY_TICKET_ISSUED,
                "Billete compensatorio " + issuance.getProduct().getProductType()
                        + " emitido correctamente",
                "ISSUED",
                occurredAt
        );
    }

    public DeviceEventLog registerFailed(
            CompensatoryTicketIssuance issuance,
            LocalDateTime occurredAt
    ) {
        return register(
                issuance,
                issuance.getIssuedTicket(),
                DeviceEventType.TICKET_PURCHASE_FAILED,
                "No se pudo presentar el billete compensatorio " + issuance.getCode(),
                "FAILED",
                occurredAt
        );
    }

    private DeviceEventLog register(
            CompensatoryTicketIssuance issuance,
            Ticket ticket,
            DeviceEventType eventType,
            String message,
            String eventStage,
            LocalDateTime occurredAt
    ) {
        DeviceEventLog log = new DeviceEventLog(
                LogOrigin.ADMINISTRATION,
                DeviceEventSource.ADMINISTRATIVE,
                eventType,
                LogSeverity.INFO,
                message,
                issuance.getTargetDevice(),
                occurredAt,
                issuance.getCode() + ":" + eventStage,
                payload(issuance, ticket, eventStage)
        );
        log.linkCompensatoryIssuance(issuance, ticket, issuance.getRequestedBy());
        return logRepository.save(log);
    }

    private String payload(
            CompensatoryTicketIssuance issuance,
            Ticket ticket,
            String eventStage
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issuanceCode", issuance.getCode());
        payload.put("eventStage", eventStage);
        payload.put("ticketType", issuance.getProduct().getProductType());
        payload.put("productCode", issuance.getProduct().getCode());
        payload.put("deviceCode", issuance.getTargetDevice().getCode());
        payload.put("operatorUsername", issuance.getRequestedBy().getUsername());
        if (ticket != null) {
            payload.put("ticketCode", ticket.getCode());
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize the ticket issuance event", exception);
        }
    }
}
