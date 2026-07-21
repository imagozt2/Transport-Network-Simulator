package com.transport.simulator.service.model;

import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.TrainPositionState;
import java.time.ZonedDateTime;
import java.util.Objects;

public record SimulatedTrainPosition(
        int dutyNumber,
        Long trainId,
        String trainCode,
        Long lineId,
        String lineCode,
        TrainPositionState state,
        ServiceDirection direction,
        Long currentStationId,
        String currentStationCode,
        Long previousStationId,
        String previousStationCode,
        Long nextStationId,
        String nextStationCode,
        int progressPercentage,
        long secondsUntilNextStation,
        ZonedDateTime estimatedArrivalAt,
        ZonedDateTime evaluatedAt
) {

    public SimulatedTrainPosition {
        if (dutyNumber <= 0) {
            throw new IllegalArgumentException("dutyNumber must be positive");
        }
        Objects.requireNonNull(trainId, "trainId must not be null");
        Objects.requireNonNull(trainCode, "trainCode must not be null");
        Objects.requireNonNull(lineId, "lineId must not be null");
        Objects.requireNonNull(lineCode, "lineCode must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(previousStationId, "previousStationId must not be null");
        Objects.requireNonNull(previousStationCode, "previousStationCode must not be null");
        Objects.requireNonNull(nextStationId, "nextStationId must not be null");
        Objects.requireNonNull(nextStationCode, "nextStationCode must not be null");
        Objects.requireNonNull(estimatedArrivalAt, "estimatedArrivalAt must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        if (progressPercentage < 0 || progressPercentage > 100 || secondsUntilNextStation < 0) {
            throw new IllegalArgumentException("Position progress and remaining time must not be negative");
        }
        if (state == TrainPositionState.AT_STATION
                && (currentStationId == null || currentStationCode == null)) {
            throw new IllegalArgumentException("A stopped train requires a current station");
        }
        if (state == TrainPositionState.BETWEEN_STATIONS
                && (currentStationId != null || currentStationCode != null)) {
            throw new IllegalArgumentException("A moving train cannot have a current station");
        }
    }
}
