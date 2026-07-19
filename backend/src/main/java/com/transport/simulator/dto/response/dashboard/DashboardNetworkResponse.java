package com.transport.simulator.dto.response.dashboard;

public record DashboardNetworkResponse(
        long activeStations,
        long activeLines
) {
}
