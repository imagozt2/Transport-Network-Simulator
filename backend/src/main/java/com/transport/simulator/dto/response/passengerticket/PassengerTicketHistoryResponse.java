package com.transport.simulator.dto.response.passengerticket;

import java.util.List;

public record PassengerTicketHistoryResponse(
        List<PassengerTicketHistoryItemResponse> items,
        String nextCursor
) {
}
