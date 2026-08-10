package com.rmm.app.core.session

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerSessionTest {
    private val now = Instant.parse("2026-08-10T12:00:00Z")

    @Test
    fun activeAccessTokenIsUsableOutsideTheSafetyMargin() {
        val session = session(accessExpiry = now.plusSeconds(31))

        assertTrue(session.hasUsableAccessToken(now))
    }

    @Test
    fun accessTokenInsideTheSafetyMarginRequiresRenewal() {
        val session = session(accessExpiry = now.plusSeconds(30))

        assertFalse(session.hasUsableAccessToken(now))
        assertTrue(session.canBeRefreshed(now))
    }

    @Test
    fun expiredRefreshTokenCannotRestoreTheSession() {
        val session = session(
            accessExpiry = now.minusSeconds(120),
            refreshExpiry = now.minusSeconds(60),
        )

        assertFalse(session.hasUsableAccessToken(now, Duration.ZERO))
        assertFalse(session.canBeRefreshed(now, Duration.ZERO))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidInstallationIdentifierIsRejected() {
        session(installationId = "not-a-uuid")
    }

    private fun session(
        accessExpiry: Instant = now.plusSeconds(1_800),
        refreshExpiry: Instant = now.plusSeconds(86_400),
        installationId: String = "0e31c063-7728-492c-bd63-6e78473ebae7",
    ) = PassengerSession(
        accessToken = "access-token",
        accessTokenExpiresAt = accessExpiry,
        refreshToken = "refresh-token",
        refreshTokenExpiresAt = refreshExpiry,
        installationId = installationId,
        user = PassengerSessionUser(
            publicId = "acec96e3-7cac-48cb-8ec7-b09b2cedb850",
            email = "passenger@example.com",
            firstName = "Ana",
            lastName = "Martinez",
            status = "ACTIVE",
            locale = "es-ES",
        ),
    )
}
