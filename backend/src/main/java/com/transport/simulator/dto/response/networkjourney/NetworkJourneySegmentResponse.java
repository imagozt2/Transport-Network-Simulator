package com.transport.simulator.dto.response.networkjourney;

import java.util.List;

public record NetworkJourneySegmentResponse(
        Long lineId,
        String lineCode,
        String lineName,
        String lineColor,
        NetworkJourneyStationResponse origin,
        NetworkJourneyStationResponse destination,
        int stopCount,
        int travelSeconds,
        List<NetworkJourneyStationResponse> stations
) {
    public NetworkJourneySegmentResponse {
        stations = List.copyOf(stations);
    }
}
