package com.transport.simulator.dto.response.depotoperation;

import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.TrainStatus;
import java.util.Map;

public record DepotFleetDistributionResponse(
        int assignedTrainCount,
        long assignedTrainsInService,
        Map<TrainStatus, Long> byStatus,
        Map<FleetRole, Long> byRole,
        Map<String, Long> bySeries
) {
    public DepotFleetDistributionResponse {
        byStatus = Map.copyOf(byStatus);
        byRole = Map.copyOf(byRole);
        bySeries = Map.copyOf(bySeries);
    }
}
