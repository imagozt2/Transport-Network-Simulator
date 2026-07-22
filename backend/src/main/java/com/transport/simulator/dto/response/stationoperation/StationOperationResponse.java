package com.transport.simulator.dto.response.stationoperation;

import com.transport.simulator.enums.StationOperationStatus;
import java.util.List;

public record StationOperationResponse(
        Long id,
        String code,
        String name,
        StationOperationStatus status,
        boolean transferStation,
        int lineCount,
        int activeLineCount,
        int activeTrainCount,
        StationOperationDevicesResponse devices,
        List<StationOperationLineResponse> lines,
        List<StationArrivalResponse> nextArrivals
) {
    public StationOperationResponse {
        lines = List.copyOf(lines);
        nextArrivals = List.copyOf(nextArrivals);
    }
}
