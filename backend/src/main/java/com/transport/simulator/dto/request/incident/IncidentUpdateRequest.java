package com.transport.simulator.dto.request.incident;

import com.transport.simulator.enums.IncidentCategory;
import com.transport.simulator.enums.IncidentPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record IncidentUpdateRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank String description,
        @NotNull IncidentCategory category,
        @NotNull IncidentPriority priority,
        @Positive Long assignedOperatorId,
        @Positive Long affectedLineId,
        @Positive Long affectedStationId,
        @Positive Long affectedTrainId,
        @Positive Long affectedDeviceId,
        @Positive Long affectedDepotId
) {
}
