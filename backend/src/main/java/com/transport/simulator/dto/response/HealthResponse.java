package com.transport.simulator.dto.response;

import java.time.Instant;

public record HealthResponse(
        String status,
        String database,
        Instant timestamp
) {
}
