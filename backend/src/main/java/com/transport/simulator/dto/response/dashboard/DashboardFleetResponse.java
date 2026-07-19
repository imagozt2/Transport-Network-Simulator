package com.transport.simulator.dto.response.dashboard;

import com.transport.simulator.enums.TrainStatus;
import java.util.Map;

public record DashboardFleetResponse(
        long activeTrains,
        Map<TrainStatus, Long> byStatus
) {
}
