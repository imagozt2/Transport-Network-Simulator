package com.transport.simulator.dto.response.incident;

import com.transport.simulator.entity.OperatorAccount;

public record IncidentOperatorResponse(
        Long id,
        String username,
        String firstName,
        String lastName
) {
    public static IncidentOperatorResponse from(OperatorAccount operator) {
        return operator == null ? null : new IncidentOperatorResponse(
                operator.getId(),
                operator.getUsername(),
                operator.getFirstName(),
                operator.getLastName()
        );
    }
}
