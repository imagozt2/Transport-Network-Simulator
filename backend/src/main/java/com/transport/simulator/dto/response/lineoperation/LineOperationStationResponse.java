package com.transport.simulator.dto.response.lineoperation;

public record LineOperationStationResponse(
        Long id,
        String code,
        String name,
        int stationOrder
) {
}
