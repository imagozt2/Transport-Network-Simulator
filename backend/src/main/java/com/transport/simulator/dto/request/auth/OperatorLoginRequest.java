package com.transport.simulator.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OperatorLoginRequest(
        @NotBlank @Size(max = 254) String identifier,
        @NotBlank @Size(max = 200) String password
) {
}
