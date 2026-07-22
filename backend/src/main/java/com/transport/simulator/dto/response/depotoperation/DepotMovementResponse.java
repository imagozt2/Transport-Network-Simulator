package com.transport.simulator.dto.response.depotoperation;

import com.transport.simulator.enums.DepotMovementStatus;
import com.transport.simulator.enums.DepotMovementType;
import java.time.ZonedDateTime;

public record DepotMovementResponse(
        int dutyNumber,
        DepotMovementType type,
        DepotMovementStatus status,
        ZonedDateTime scheduledAt,
        Long secondsUntilMovement,
        DepotMovementTrainResponse train,
        DepotMovementLineResponse line,
        DepotOperationStationResponse terminal
) {
}
