package com.transport.simulator.dto.response.passenger;

import java.time.Instant;

public record PassengerSessionResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        PassengerRegistrationUserResponse user
) {
}
