package com.transport.simulator.service.model;

import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.TrainPositionState;
import com.transport.simulator.enums.TrainStatus;
import java.time.ZonedDateTime;
import java.util.Objects;

public record SimulatedTrainState(
        Long trainId,
        String trainCode,
        String trainSeries,
        FleetRole fleetRole,
        TrainStatus status,
        Long assignedLineId,
        String assignedLineCode,
        Long currentLineId,
        String currentLineCode,
        Long currentDepotId,
        String currentDepotCode,
        Integer dutyNumber,
        TrainPositionState positionState,
        ServiceDirection direction,
        Long currentStationId,
        String currentStationCode,
        Long previousStationId,
        String previousStationCode,
        Long nextStationId,
        String nextStationCode,
        Integer progressPercentage,
        Long secondsUntilNextStation,
        ZonedDateTime estimatedArrivalAt
) {

    public SimulatedTrainState {
        Objects.requireNonNull(trainId, "trainId must not be null");
        Objects.requireNonNull(trainCode, "trainCode must not be null");
        Objects.requireNonNull(trainSeries, "trainSeries must not be null");
        Objects.requireNonNull(fleetRole, "fleetRole must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(assignedLineId, "assignedLineId must not be null");
        Objects.requireNonNull(assignedLineCode, "assignedLineCode must not be null");
        if (status == TrainStatus.IN_SERVICE) {
            if (fleetRole != FleetRole.REGULAR_SERVICE || !"9000".equals(trainSeries)) {
                throw new IllegalArgumentException("Only regular-service 9000 series trains can be in service");
            }
            Objects.requireNonNull(currentLineId, "An in-service train requires a current line");
            Objects.requireNonNull(currentLineCode, "An in-service train requires a current line code");
            Objects.requireNonNull(dutyNumber, "An in-service train requires a duty");
            Objects.requireNonNull(positionState, "An in-service train requires a position state");
            Objects.requireNonNull(direction, "An in-service train requires a direction");
            Objects.requireNonNull(previousStationId, "An in-service train requires a previous station");
            Objects.requireNonNull(previousStationCode, "An in-service train requires a previous station code");
            Objects.requireNonNull(nextStationId, "An in-service train requires a next station");
            Objects.requireNonNull(nextStationCode, "An in-service train requires a next station code");
            Objects.requireNonNull(progressPercentage, "An in-service train requires progress");
            Objects.requireNonNull(secondsUntilNextStation, "An in-service train requires an arrival countdown");
            Objects.requireNonNull(estimatedArrivalAt, "An in-service train requires an estimated arrival");
            if (currentDepotId != null || currentDepotCode != null) {
                throw new IllegalArgumentException("An in-service train cannot be inside a depot");
            }
        } else if (status == TrainStatus.DEPOT) {
            Objects.requireNonNull(currentDepotId, "A depot train requires a current depot");
            Objects.requireNonNull(currentDepotCode, "A depot train requires a current depot code");
            if (currentLineId != null
                    || currentLineCode != null
                    || dutyNumber != null
                    || positionState != null
                    || direction != null
                    || currentStationId != null
                    || currentStationCode != null
                    || previousStationId != null
                    || previousStationCode != null
                    || nextStationId != null
                    || nextStationCode != null
                    || progressPercentage != null
                    || secondsUntilNextStation != null
                    || estimatedArrivalAt != null) {
                throw new IllegalArgumentException("A depot train cannot contain an active service position");
            }
        }
    }
}
