package com.transport.simulator.dto.response.passengerticket;

import com.transport.simulator.entity.TicketOperation;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PassengerTicketHistoryItemResponse(
        String type,
        String resultingStatus,
        PassengerTicketStationResponse station,
        BigDecimal operationAmount,
        BigDecimal balanceAfter,
        Integer remainingTripsAfter,
        LocalDateTime validFromAfter,
        LocalDateTime validUntilAfter,
        String currency,
        LocalDateTime occurredAt
) {
    public static PassengerTicketHistoryItemResponse from(TicketOperation operation) {
        return new PassengerTicketHistoryItemResponse(
                operation.getType().name(), operation.getResultingStatus().name(),
                PassengerTicketStationResponse.from(operation.getStation()),
                operation.getOperationAmount(), operation.getBalanceAfter(),
                operation.getRemainingTripsAfter(), operation.getValidFromAfter(),
                operation.getValidUntilAfter(), operation.getCurrency(), operation.getOccurredAt()
        );
    }
}
