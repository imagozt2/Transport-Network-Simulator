package com.transport.simulator.service.model;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.enums.TicketStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketSnapshot(
        TicketStatus status,
        BigDecimal balance,
        Integer remainingTrips,
        LocalDateTime validFrom,
        LocalDateTime validUntil
) {
    public static TicketSnapshot from(Ticket ticket) {
        return new TicketSnapshot(
                ticket.getStatus(), ticket.getBalanceAmount(), ticket.getRemainingTrips(),
                ticket.getValidFrom(), ticket.getValidUntil()
        );
    }
}
