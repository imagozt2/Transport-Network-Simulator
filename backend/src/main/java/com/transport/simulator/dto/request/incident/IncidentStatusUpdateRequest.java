package com.transport.simulator.dto.request.incident;

import com.transport.simulator.enums.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidentStatusUpdateRequest(
        @NotNull IncidentStatus status,
        @Size(max = 500) String note,
        String resolutionSummary
) {
}
