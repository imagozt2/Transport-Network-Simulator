package com.transport.simulator.dto.response.incident;

import com.transport.simulator.entity.IncidentStatusChange;
import com.transport.simulator.enums.IncidentStatus;
import java.time.LocalDateTime;

public record IncidentStatusChangeResponse(
        Long id,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        String note,
        IncidentOperatorResponse changedBy,
        LocalDateTime createdAt
) {
    public static IncidentStatusChangeResponse from(IncidentStatusChange change) {
        return new IncidentStatusChangeResponse(
                change.getId(),
                change.getPreviousStatus(),
                change.getNewStatus(),
                change.getChangeNote(),
                IncidentOperatorResponse.from(change.getChangedBy()),
                change.getCreatedAt()
        );
    }
}
