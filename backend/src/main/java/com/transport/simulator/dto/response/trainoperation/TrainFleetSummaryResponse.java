package com.transport.simulator.dto.response.trainoperation;

import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.TrainStatus;
import java.util.Map;

public record TrainFleetSummaryResponse(
        int activeFleet,
        long trainsInService,
        long trainsInDepots,
        Map<TrainStatus, Long> byStatus,
        Map<FleetRole, Long> byRole,
        Map<String, Long> bySeries
) {
    public TrainFleetSummaryResponse {
        byStatus = Map.copyOf(byStatus);
        byRole = Map.copyOf(byRole);
        bySeries = Map.copyOf(bySeries);
    }
}
