package com.transport.simulator.dto.response.incident;

import java.util.List;

public record IncidentsPageResponse(
        IncidentSummaryResponse summary,
        List<IncidentResponse> incidents,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {
}
