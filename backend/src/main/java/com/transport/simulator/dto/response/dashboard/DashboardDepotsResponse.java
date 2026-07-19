package com.transport.simulator.dto.response.dashboard;

import java.util.List;

public record DashboardDepotsResponse(
        long activeDepots,
        long totalCapacity,
        long assignedTrains,
        long freeSlots,
        int occupationPercentage,
        List<DashboardDepotResponse> items
) {
}
