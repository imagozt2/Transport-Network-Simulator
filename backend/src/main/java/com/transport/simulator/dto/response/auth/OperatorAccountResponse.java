package com.transport.simulator.dto.response.auth;

import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.enums.OperatorAccountStatus;
import com.transport.simulator.enums.OperatorRole;
import java.time.LocalDateTime;

public record OperatorAccountResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        OperatorRole role,
        OperatorAccountStatus status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {

    public static OperatorAccountResponse from(OperatorAccount account) {
        return new OperatorAccountResponse(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getRole(),
                account.getStatus(),
                account.getLastLoginAt(),
                account.getCreatedAt()
        );
    }

}
