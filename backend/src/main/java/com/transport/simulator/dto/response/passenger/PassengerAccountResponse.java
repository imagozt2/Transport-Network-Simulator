package com.transport.simulator.dto.response.passenger;

import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.enums.PassengerAccountStatus;
import java.time.LocalDateTime;

public record PassengerAccountResponse(
        String publicId,
        String email,
        String firstName,
        String lastName,
        PassengerAccountStatus status,
        boolean emailVerified,
        LocalDateTime emailVerifiedAt,
        LocalDateTime lastLoginAt,
        LocalDateTime registeredAt,
        LocalDateTime updatedAt
) {

    public static PassengerAccountResponse from(PassengerAccount account) {
        return new PassengerAccountResponse(
                account.getPublicId(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getStatus(),
                account.getEmailVerifiedAt() != null,
                account.getEmailVerifiedAt(),
                account.getLastLoginAt(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
