package com.transport.simulator.dto.response.depotoperation;

import com.transport.simulator.enums.DepotOperationStatus;

public record DepotOperationResponse(
        Long id,
        String code,
        String name,
        DepotOperationStationResponse station,
        int capacity,
        int trackCount,
        int trainsPerTrack,
        long occupiedSpaces,
        long availableSpaces,
        int occupancyPercentage,
        DepotOperationStatus status,
        DepotFleetDistributionResponse fleet
) {
}
