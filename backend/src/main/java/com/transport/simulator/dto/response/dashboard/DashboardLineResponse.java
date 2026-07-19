package com.transport.simulator.dto.response.dashboard;

public record DashboardLineResponse(
        Long id,
        String code,
        String name,
        String color
) {
}
