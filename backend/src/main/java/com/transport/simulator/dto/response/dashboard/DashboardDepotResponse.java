package com.transport.simulator.dto.response.dashboard;

public record DashboardDepotResponse(
        Long id,
        String code,
        String name,
        int capacity,
        long assignedTrains,
        long freeSlots
) {
}
