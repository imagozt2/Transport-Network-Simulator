package com.transport.simulator.dto.response.passengernetwork;

import java.util.List;

public record PassengerNetworkJourneySegmentResponse(
        String lineCode,
        String lineName,
        String lineColor,
        PassengerNetworkJourneyStationResponse directionTerminal,
        int stopCount,
        int travelSeconds,
        List<PassengerNetworkJourneyStationResponse> stations
) {
    public PassengerNetworkJourneySegmentResponse {
        stations = List.copyOf(stations);
    }
}
