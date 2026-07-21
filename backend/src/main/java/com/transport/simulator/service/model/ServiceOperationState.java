package com.transport.simulator.service.model;

import com.transport.simulator.enums.ServiceOperationPhase;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

public record ServiceOperationState(
        ZonedDateTime evaluatedAt,
        ServiceOperationPhase phase,
        int activeLineCount,
        List<LineServiceOperationState> lines
) {

    public ServiceOperationState {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        lines = List.copyOf(lines);
        long actualActiveLines = lines.stream().filter(LineServiceOperationState::serviceOpen).count();
        if (activeLineCount != actualActiveLines) {
            throw new IllegalArgumentException("activeLineCount does not match the line states");
        }
        if ((phase == ServiceOperationPhase.CLOSED) != (activeLineCount == 0)) {
            throw new IllegalArgumentException("The network phase does not match its active lines");
        }
    }

    public boolean serviceOpen() {
        return phase != ServiceOperationPhase.CLOSED;
    }
}
