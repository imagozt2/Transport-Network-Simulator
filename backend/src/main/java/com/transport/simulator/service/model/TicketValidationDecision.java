package com.transport.simulator.service.model;

import com.transport.simulator.entity.TicketValidation;
import com.transport.simulator.enums.TicketValidationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketValidationDecision(
        String validationReference,
        TicketValidationStatus decision,
        String reasonCode,
        String ticketCode,
        String validAtStationCode,
        BigDecimal fareAmount,
        LocalDateTime decidedAt
) {
    public static TicketValidationDecision from(TicketValidation validation) {
        return new TicketValidationDecision(
                validation.getExternalReference(), validation.getStatus(),
                validation.getRejectionReason() == null ? "VALID" : validation.getRejectionReason(),
                validation.getTicket() == null ? null : validation.getTicket().getCode(),
                validation.getStation().getCode(), validation.getFareAmount(),
                validation.getValidatedAt()
        );
    }
}
