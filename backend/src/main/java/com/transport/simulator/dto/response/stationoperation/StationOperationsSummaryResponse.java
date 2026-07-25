package com.transport.simulator.dto.response.stationoperation;

public record StationOperationsSummaryResponse(
        int stationCount,
        int activeStationCount,
        int transferStationCount,
        long ticketMachineCount,
        long entryValidatorCount,
        long exitValidatorCount
) {
}
