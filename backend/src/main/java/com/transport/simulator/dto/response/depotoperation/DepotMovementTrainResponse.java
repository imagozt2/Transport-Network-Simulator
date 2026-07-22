package com.transport.simulator.dto.response.depotoperation;

import com.transport.simulator.enums.FleetRole;

public record DepotMovementTrainResponse(
        Long id,
        String code,
        String series,
        FleetRole fleetRole
) {
}
