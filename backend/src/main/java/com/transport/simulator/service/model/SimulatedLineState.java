package com.transport.simulator.service.model;

import java.util.Objects;
import java.util.Optional;

public record SimulatedLineState(
        LineServiceOperationState operation,
        Optional<LineDutyPlan> dutyPlan
) {

    public SimulatedLineState {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(dutyPlan, "dutyPlan must not be null");
        if (operation.serviceOpen() != dutyPlan.isPresent()) {
            throw new IllegalArgumentException("An open line requires a duty plan and a closed line cannot have one");
        }
        dutyPlan.ifPresent(plan -> {
            if (!plan.lineId().equals(operation.lineId())) {
                throw new IllegalArgumentException("The operation and duty plan must belong to the same line");
            }
        });
    }
}
