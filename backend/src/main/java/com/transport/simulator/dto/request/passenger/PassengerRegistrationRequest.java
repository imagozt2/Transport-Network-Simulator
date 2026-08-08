package com.transport.simulator.dto.request.passenger;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PassengerRegistrationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 150) String lastName,
        @NotBlank @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$") String locale,
        @NotBlank @Size(max = 30) String termsVersion
) {
}
