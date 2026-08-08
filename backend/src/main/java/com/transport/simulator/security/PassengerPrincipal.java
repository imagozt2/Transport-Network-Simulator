package com.transport.simulator.security;

public record PassengerPrincipal(
        Long accountId,
        String publicId,
        Long sessionId,
        String installationId
) {
}
