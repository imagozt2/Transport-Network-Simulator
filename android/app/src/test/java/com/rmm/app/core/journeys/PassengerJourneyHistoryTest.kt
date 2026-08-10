package com.rmm.app.core.journeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengerJourneyHistoryTest {
    @Test
    fun recordingMovesAnExistingRouteToTheFrontWithoutDuplicatingIt() {
        val route = journey(1)
        val history = PassengerJourneyHistory(recent = listOf(journey(2), route))

        val updated = history.recording(route.copy(savedAtEpochMillis = 99))

        assertEquals(listOf(route.routeKey, journey(2).routeKey), updated.recent.map { it.routeKey })
        assertEquals(1, updated.recent.count { it.routeKey == route.routeKey })
    }

    @Test
    fun recordingRespectsTheConfiguredLimit() {
        val history = (1..10).fold(PassengerJourneyHistory()) { current, index ->
            current.recording(journey(index), maximumRecent = 3)
        }

        assertEquals(listOf("ST010>ST110", "ST009>ST109", "ST008>ST108"), history.recent.map { it.routeKey })
    }

    @Test
    fun favoriteCanBeAddedAndRemoved() {
        val route = journey(1)
        val favorite = PassengerJourneyHistory().togglingFavorite(route)

        assertTrue(favorite.favorites.any { it.routeKey == route.routeKey })
        assertTrue(favorite.togglingFavorite(route).favorites.isEmpty())
    }

    private fun journey(index: Int) = SavedPassengerJourney(
        originCode = "ST%03d".format(index),
        originName = "Origen $index",
        destinationCode = "ST%03d".format(index + 100),
        destinationName = "Destino $index",
        savedAtEpochMillis = index.toLong(),
    )
}
