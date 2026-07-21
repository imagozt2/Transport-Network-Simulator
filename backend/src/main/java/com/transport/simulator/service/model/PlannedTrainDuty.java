package com.transport.simulator.service.model;

import com.transport.simulator.enums.ServiceDirection;
import java.time.ZonedDateTime;
import java.util.Objects;

public record PlannedTrainDuty(
        int dutyNumber,
        ServiceDirection initialDirection,
        Long originStationId,
        String originStationCode,
        String startingPeriodCode,
        int startingHeadwaySeconds,
        ZonedDateTime plannedStartAt,
        ZonedDateTime plannedReleaseAt
) {

    public PlannedTrainDuty {
        if (dutyNumber <= 0) {
            throw new IllegalArgumentException("dutyNumber must be positive");
        }
        Objects.requireNonNull(initialDirection, "initialDirection must not be null");
        Objects.requireNonNull(originStationId, "originStationId must not be null");
        Objects.requireNonNull(originStationCode, "originStationCode must not be null");
        Objects.requireNonNull(startingPeriodCode, "startingPeriodCode must not be null");
        Objects.requireNonNull(plannedStartAt, "plannedStartAt must not be null");
        Objects.requireNonNull(plannedReleaseAt, "plannedReleaseAt must not be null");
        if (startingHeadwaySeconds <= 0) {
            throw new IllegalArgumentException("startingHeadwaySeconds must be positive");
        }
        if (plannedReleaseAt.isBefore(plannedStartAt)) {
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
