package com.transport.simulator.dto.response.stationoperation;

import com.transport.simulator.enums.ServiceOperationPhase;
import java.time.ZonedDateTime;
import java.util.List;

public record StationOperationsResponse(
        ZonedDateTime evaluatedAt,
        ServiceOperationPhase phase,
        int stationCount,
        int activeStationCount,
        List<StationOperationResponse> stations
) {
    public StationOperationsResponse {
        stations = List.copyOf(stations);
    }
}
