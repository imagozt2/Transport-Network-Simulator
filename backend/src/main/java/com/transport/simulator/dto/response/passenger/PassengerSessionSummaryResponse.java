package com.transport.simulator.dto.response.passenger;

import com.transport.simulator.entity.PassengerSession;
import com.transport.simulator.enums.PassengerDevicePlatform;
import java.time.Instant;
import java.time.ZoneOffset;

public record PassengerSessionSummaryResponse(
        String sessionId,
        String installationId,
        String deviceName,
        PassengerDevicePlatform platform,
        Instant lastUsedAt,
        Instant refreshTokenExpiresAt,
        boolean current
) {
    public static PassengerSessionSummaryResponse from(PassengerSession session, Long currentId) {
        return new PassengerSessionSummaryResponse(
                session.getPublicId(),
                session.getInstallationId(),
                session.getDeviceName(),
                session.getPlatform(),
                session.getLastUsedAt().toInstant(ZoneOffset.UTC),
                session.getRefreshTokenExpiresAt().toInstant(ZoneOffset.UTC),
                session.getId().equals(currentId)
        );
    }
}
