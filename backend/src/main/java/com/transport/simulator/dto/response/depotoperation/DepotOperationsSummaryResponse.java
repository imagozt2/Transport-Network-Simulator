package com.transport.simulator.dto.response.depotoperation;

public record DepotOperationsSummaryResponse(
        int depotCount,
        long totalCapacity,
        long occupiedSpaces,
        long availableSpaces,
        int occupancyPercentage,
        long assignedFleet,
        long trainsInService
) {
}
