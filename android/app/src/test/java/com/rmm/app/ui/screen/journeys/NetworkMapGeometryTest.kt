package com.rmm.app.ui.screen.journeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkMapGeometryTest {
    @Test
    fun everyLineReferencesKnownStationsWithoutDuplicates() {
        NetworkMapGeometry.lines.forEach { line ->
            assertTrue(line.stationCodes.all(NetworkMapGeometry.stations::containsKey))
            assertEquals(line.stationCodes.size, line.stationCodes.distinct().size)
        }
    }

    @Test
    fun interchangeStationsShareExactlyTheSameMapPoint() {
        val occurrences = NetworkMapGeometry.lines
            .flatMap { line -> line.stationCodes.map { station -> station to line.code } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }

        assertTrue(occurrences.isNotEmpty())
        occurrences.keys.forEach { stationCode ->
            assertTrue(NetworkMapGeometry.stations.containsKey(stationCode))
        }
    }
}
