package com.transport.simulator.dto.response.incident;

public record IncidentSummaryResponse(
        long total,
        long open,
        long inProgress,
        long resolved,
        long closed,
        long cancelled
) {
}
