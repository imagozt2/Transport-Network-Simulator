package com.transport.simulator.dto.response.networkjourney;

import java.util.List;

public record NetworkJourneyResponse(
        NetworkJourneyStationResponse origin,
        NetworkJourneyStationResponse destination,
        int stationCount,
        int transferCount,
        int estimatedDurationSeconds,
        List<NetworkJourneyStationResponse> stations,
        List<NetworkJourneySegmentResponse> segments
) {
    public NetworkJourneyResponse {
        stations = List.copyOf(stations);
        segments = List.copyOf(segments);
    }
}
