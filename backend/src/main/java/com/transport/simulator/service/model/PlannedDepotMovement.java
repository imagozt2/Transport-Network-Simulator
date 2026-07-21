package com.transport.simulator.service.model;

import com.transport.simulator.enums.DepotMovementType;
import java.time.ZonedDateTime;
import java.util.Objects;

public record PlannedDepotMovement(
        int dutyNumber,
        Long trainId,
        String trainCode,
        Long lineId,
        String lineCode,
        Long depotId,
        String depotCode,
        Long stationId,
        String stationCode,
        DepotMovementType movementType,
        ZonedDateTime scheduledAt
) {

    public PlannedDepotMovement {
        if (dutyNumber <= 0) {
            throw new IllegalArgumentException("dutyNumber must be positive");
        }
        Objects.requireNonNull(trainId, "trainId must not be null");
        Objects.requireNonNull(trainCode, "trainCode must not be null");
        Objects.requireNonNull(lineId, "lineId must not be null");
        Objects.requireNonNull(lineCode, "lineCode must not be null");
        Objects.requireNonNull(depotId, "depotId must not be null");
        Objects.requireNonNull(depotCode, "depotCode must not be null");
        Objects.requireNonNull(stationId, "stationId must not be null");
        Objects.requireNonNull(stationCode, "stationCode must not be null");
        Objects.requireNonNull(movementType, "movementType must not be null");
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
    }

    public boolean hasOccurredAt(ZonedDateTime instant) {
        return !instant.isBefore(scheduledAt);
    }
}
