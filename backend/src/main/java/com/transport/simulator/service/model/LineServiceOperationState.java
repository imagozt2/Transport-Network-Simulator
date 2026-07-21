package com.transport.simulator.service.model;

import com.transport.simulator.enums.ServiceOperationPhase;
import java.util.Objects;
import java.util.Optional;

public record LineServiceOperationState(
        Long lineId,
        String lineCode,
        ServiceOperationPhase phase,
        Optional<ResolvedLineServiceConfiguration> configuration,
        long elapsedServiceSeconds,
        long remainingServiceSeconds
) {

    public LineServiceOperationState {
        Objects.requireNonNull(lineId, "lineId must not be null");
        Objects.requireNonNull(lineCode, "lineCode must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(configuration, "configuration must not be null");
        if (elapsedServiceSeconds < 0 || remainingServiceSeconds < 0) {
            throw new IllegalArgumentException("Service durations must not be negative");
        }
        if ((phase == ServiceOperationPhase.CLOSED) != configuration.isEmpty()) {
            throw new IllegalArgumentException("Closed lines must not contain an active configuration");
        }
    }

    public boolean serviceOpen() {
        return phase != ServiceOperationPhase.CLOSED;
    }

    public static LineServiceOperationState closed(Long lineId, String lineCode) {
        return new LineServiceOperationState(
                lineId,
                lineCode,
                ServiceOperationPhase.CLOSED,
                Optional.empty(),
                0,
                0
        );
    }
}
