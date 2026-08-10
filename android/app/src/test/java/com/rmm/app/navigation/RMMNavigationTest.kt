package com.rmm.app.navigation

import com.rmm.app.core.session.PassengerSession
import com.rmm.app.core.session.PassengerSessionUser
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RMMNavigationTest {
    @Test
    fun unauthenticatedUsersStartAtAuthentication() {
        assertEquals(RMMRootDestination.AUTHENTICATION, resolveRootDestination(null))
    }

    @Test
    fun authenticatedUsersStartAtApplication() {
        assertEquals(RMMRootDestination.APPLICATION, resolveRootDestination(session()))
    }

    @Test
    fun topLevelNavigationKeepsTheExpectedUniqueRoutes() {
        val routes = RMMTopLevelDestination.entries.map(RMMTopLevelDestination::route)

        assertEquals(listOf("home", "tickets", "journeys", "account"), routes)
        assertEquals(routes.size, routes.distinct().size)
    }

    private fun session() = PassengerSession(
        accessToken = "access-token",
        accessTokenExpiresAt = Instant.parse("2026-08-10T12:30:00Z"),
        refreshToken = "refresh-token",
        refreshTokenExpiresAt = Instant.parse("2026-09-10T12:00:00Z"),
        installationId = "0e31c063-7728-492c-bd63-6e78473ebae7",
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
