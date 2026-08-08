package com.transport.simulator.dto.request.passenger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PassengerEmailVerificationRequest(
        @NotBlank @Size(max = 200) String verificationToken
) {
}
