package com.transport.simulator.dto.response.networkmap;

public record NetworkMapStationResponse(
        Long id,
        String code,
        String name,
        int stationOrder
) {
}
