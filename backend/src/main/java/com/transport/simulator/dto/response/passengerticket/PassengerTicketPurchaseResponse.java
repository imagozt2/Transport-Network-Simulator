package com.transport.simulator.dto.response.passengerticket;

import com.transport.simulator.entity.Purchase;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PassengerTicketPurchaseResponse(
        String code,
        String status,
        String productCode,
        BigDecimal totalAmount,
        String currency,
        String ticketCode,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {
    public static PassengerTicketPurchaseResponse from(Purchase purchase) {
        return new PassengerTicketPurchaseResponse(
                purchase.getCode(),
                purchase.getStatus().name(),
                purchase.getProduct().getCode(),
                purchase.getTotalAmount(),
                purchase.getTicket().getCurrency(),
                purchase.getTicket().getCode(),
                purchase.getRequestedAt(),
                purchase.getCompletedAt()
        );
    }
}
