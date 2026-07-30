package com.transport.simulator.dto.response.auth;

public record CsrfTokenResponse(
        String headerName,
        String parameterName,
        String token
) {
}
