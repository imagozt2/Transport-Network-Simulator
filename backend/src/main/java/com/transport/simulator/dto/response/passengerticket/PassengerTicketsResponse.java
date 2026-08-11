package com.transport.simulator.dto.response.passengerticket;

import java.util.List;

public record PassengerTicketsResponse(
        List<PassengerTicketSummaryResponse> items,
        String nextCursor
) {
    public PassengerTicketsResponse {
        items = List.copyOf(items);
    }
}
