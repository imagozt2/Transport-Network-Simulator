package com.transport.simulator.dto.request.passenger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PassengerPasswordResetRequest(
        @NotBlank @Size(max = 200) String resetToken,
        @NotBlank @Size(min = 12, max = 72) String newPassword
) {
}
