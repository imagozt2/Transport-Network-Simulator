package com.transport.simulator.dto.response.passenger;

public record PassengerRegistrationResponse(
        PassengerRegistrationUserResponse user,
        boolean verificationRequired
) {
}
