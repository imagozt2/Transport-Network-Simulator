package com.transport.simulator.service.model;

import com.transport.simulator.enums.ServiceDirection;
import java.time.ZonedDateTime;
import java.util.Objects;

public record PlannedTrainDuty(
        int dutyNumber,
        Long trainId,
        String trainCode,
        String trainSeries,
        Long homeDepotId,
        String homeDepotCode,
        ServiceDirection initialDirection,
        Long originStationId,
        String originStationCode,
        String startingPeriodCode,
        int startingHeadwaySeconds,
        ZonedDateTime plannedStartAt,
        ZonedDateTime requestedReleaseAt,
        ZonedDateTime plannedReleaseAt
) {

    public PlannedTrainDuty {
        if (dutyNumber <= 0) {
            throw new IllegalArgumentException("dutyNumber must be positive");
        }
        Objects.requireNonNull(trainId, "trainId must not be null");
        Objects.requireNonNull(trainCode, "trainCode must not be null");
        Objects.requireNonNull(trainSeries, "trainSeries must not be null");
        Objects.requireNonNull(homeDepotId, "homeDepotId must not be null");
        Objects.requireNonNull(homeDepotCode, "homeDepotCode must not be null");
        if (!"9000".equals(trainSeries)) {
            throw new IllegalArgumentException("Regular service duties require a 9000 series train");
        }
        Objects.requireNonNull(initialDirection, "initialDirection must not be null");
        Objects.requireNonNull(originStationId, "originStationId must not be null");
        Objects.requireNonNull(originStationCode, "originStationCode must not be null");
        Objects.requireNonNull(startingPeriodCode, "startingPeriodCode must not be null");
        Objects.requireNonNull(plannedStartAt, "plannedStartAt must not be null");
        Objects.requireNonNull(requestedReleaseAt, "requestedReleaseAt must not be null");
        Objects.requireNonNull(plannedReleaseAt, "plannedReleaseAt must not be null");
        if (startingHeadwaySeconds <= 0) {
            throw new IllegalArgumentException("startingHeadwaySeconds must be positive");
        }
        if (requestedReleaseAt.isBefore(plannedStartAt)
                || plannedReleaseAt.isBefore(requestedReleaseAt)) {
            throw new IllegalArgumentException("A duty cannot end before it starts");
        }
    }

    public boolean isStartedAt(ZonedDateTime instant) {
        return !instant.isBefore(plannedStartAt);
    }

    public boolean isActiveAt(ZonedDateTime instant) {
        return isStartedAt(instant) && instant.isBefore(plannedReleaseAt);
    }
}
