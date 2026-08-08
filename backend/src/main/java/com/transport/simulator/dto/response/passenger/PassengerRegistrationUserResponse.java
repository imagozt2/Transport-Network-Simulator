package com.transport.simulator.dto.response.passenger;

import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.enums.PassengerAccountStatus;

public record PassengerRegistrationUserResponse(
        String publicId,
        String email,
        String firstName,
        String lastName,
        PassengerAccountStatus status,
        String locale
) {
    public static PassengerRegistrationUserResponse from(PassengerAccount account) {
        return new PassengerRegistrationUserResponse(
                account.getPublicId(), account.getEmail(), account.getFirstName(),
                account.getLastName(), account.getStatus(), account.getPreferredLocale()
        );
    }
}
