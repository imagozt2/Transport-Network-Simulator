package com.transport.simulator.service.model;

import com.transport.simulator.entity.TicketValidation;
import com.transport.simulator.enums.TicketProductType;
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
        BigDecimal remainingBalance,
        Integer consumedTrips,
        Integer remainingTrips,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        LocalDateTime decidedAt
) {
    public static TicketValidationDecision from(TicketValidation validation) {
        return new TicketValidationDecision(
                validation.getExternalReference(), validation.getStatus(),
                validation.getRejectionReason() == null ? "VALID" : validation.getRejectionReason(),
                validation.getTicket() == null ? null : validation.getTicket().getCode(),
                validation.getStation().getCode(), validation.getFareAmount(),
                remainingBalance(validation), consumedTrips(validation),
                validation.getRemainingTripsAfter(), validation.getValidFrom(),
                validation.getValidUntil(),
                validation.getValidatedAt()
        );
    }

    private static Integer consumedTrips(TicketValidation validation) {
        Integer before = validation.getRemainingTripsBefore();
        Integer after = validation.getRemainingTripsAfter();
        if (before == null || after == null) {
            return null;
        }
        return Math.max(0, before - after);
    }

    private static BigDecimal remainingBalance(TicketValidation validation) {
        return validation.getTicket() != null
                && validation.getTicket().getProductType() == TicketProductType.SMART_BALANCE
                ? validation.getBalanceAfter() : null;
    }
}
