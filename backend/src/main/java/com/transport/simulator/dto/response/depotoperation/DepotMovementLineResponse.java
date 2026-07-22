package com.transport.simulator.dto.response.depotoperation;

public record DepotMovementLineResponse(
        Long id,
        String code,
        String name,
        String color
) {
}
