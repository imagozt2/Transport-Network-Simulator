package com.transport.simulator.dto.response.passengerticket;

import com.transport.simulator.entity.Ticket;

public record PassengerTicketProductSummaryResponse(String code, String name, String type) {
    public static PassengerTicketProductSummaryResponse from(Ticket ticket) {
        return new PassengerTicketProductSummaryResponse(
                ticket.getProduct().getCode(),
                ticket.getProduct().getName(),
                ticket.getProductType().name()
        );
    }
}
