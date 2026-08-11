package com.transport.simulator.dto.response.passengerjourney;

import java.util.List;

public record PassengerJourneyHistoryResponse(
        List<PassengerJourneyHistoryItemResponse> items,
        String nextCursor
) {
}
