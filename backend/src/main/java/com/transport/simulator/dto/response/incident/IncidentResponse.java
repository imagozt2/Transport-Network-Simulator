package com.transport.simulator.dto.response.incident;

import com.transport.simulator.entity.Incident;
import com.transport.simulator.enums.IncidentCategory;
import com.transport.simulator.enums.IncidentPriority;
import com.transport.simulator.enums.IncidentStatus;
import java.time.LocalDateTime;
import java.util.List;

public record IncidentResponse(
        String code,
        String title,
        String description,
        IncidentCategory category,
        IncidentPriority priority,
        IncidentStatus status,
        IncidentOperatorResponse createdBy,
        IncidentOperatorResponse assignedTo,
        IncidentResourceResponse affectedLine,
        IncidentResourceResponse affectedStation,
        IncidentResourceResponse affectedTrain,
        IncidentResourceResponse affectedDevice,
        IncidentResourceResponse affectedDepot,
        String resolutionSummary,
        LocalDateTime openedAt,
        LocalDateTime assignedAt,
        LocalDateTime resolvedAt,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<IncidentStatusChangeResponse> statusHistory,
        List<IncidentCommentResponse> comments
) {
    public static IncidentResponse from(Incident incident) {
        return from(incident, List.of(), List.of());
    }

    public static IncidentResponse from(
            Incident incident,
            List<IncidentStatusChangeResponse> history,
            List<IncidentCommentResponse> comments
    ) {
        return new IncidentResponse(
                incident.getCode(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getCategory(),
                incident.getPriority(),
                incident.getStatus(),
                IncidentOperatorResponse.from(incident.getCreatedBy()),
                IncidentOperatorResponse.from(incident.getAssignedTo()),
                incident.getAffectedLine() == null ? null : new IncidentResourceResponse(
                        incident.getAffectedLine().getId(), incident.getAffectedLine().getCode(),
                        incident.getAffectedLine().getName()),
                incident.getAffectedStation() == null ? null : new IncidentResourceResponse(
                        incident.getAffectedStation().getId(), incident.getAffectedStation().getCode(),
                        incident.getAffectedStation().getName()),
                incident.getAffectedTrain() == null ? null : new IncidentResourceResponse(
                        incident.getAffectedTrain().getId(), incident.getAffectedTrain().getCode(),
                        incident.getAffectedTrain().getCode()),
                incident.getAffectedDevice() == null ? null : new IncidentResourceResponse(
                        incident.getAffectedDevice().getId(), incident.getAffectedDevice().getCode(),
                        incident.getAffectedDevice().getName()),
                incident.getAffectedDepot() == null ? null : new IncidentResourceResponse(
                        incident.getAffectedDepot().getId(), incident.getAffectedDepot().getCode(),
                        incident.getAffectedDepot().getName()),
                incident.getResolutionSummary(),
                incident.getOpenedAt(),
                incident.getAssignedAt(),
                incident.getResolvedAt(),
                incident.getClosedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                history,
                comments
        );
    }
}
