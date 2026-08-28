package com.transport.simulator.dto.response.passengernetwork;

import java.util.List;

public record PassengerNetworkJourneyResponse(
        PassengerNetworkJourneyStationResponse origin,
        PassengerNetworkJourneyStationResponse destination,
        int stationCount,
        int transferCount,
        int estimatedDurationSeconds,
        List<PassengerNetworkJourneySegmentResponse> segments
) {
    public PassengerNetworkJourneyResponse {
        segments = List.copyOf(segments);
    }
}
