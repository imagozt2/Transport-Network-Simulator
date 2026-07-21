package com.transport.simulator.service.model;

import com.transport.simulator.enums.ServicePeriodType;
import java.time.ZonedDateTime;
import java.util.Objects;

public record ServicePeriodFleetPlan(
        String periodCode,
        ServicePeriodType periodType,
        ZonedDateTime startsAt,
        ZonedDateTime endsAt,
        int headwaySeconds,
        int targetFleetSize
) {

    public ServicePeriodFleetPlan {
        Objects.requireNonNull(periodCode, "periodCode must not be null");
        Objects.requireNonNull(periodType, "periodType must not be null");
        Objects.requireNonNull(startsAt, "startsAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("A service period must end after it starts");
        }
        if (headwaySeconds <= 0 || targetFleetSize <= 0) {
            throw new IllegalArgumentException("Headway and target fleet size must be positive");
        }
    }
}
