package com.transport.simulator.service.model;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

public record ServiceDutyPlan(
        ZonedDateTime evaluatedAt,
        List<LineDutyPlan> lines
) {

    public ServiceDutyPlan {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        lines = List.copyOf(lines);
    }
}
