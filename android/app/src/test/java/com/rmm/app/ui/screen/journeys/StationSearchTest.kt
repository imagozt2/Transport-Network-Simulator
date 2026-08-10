package com.rmm.app.ui.screen.journeys

import com.rmm.app.core.networkcatalog.PassengerNetworkStation
import org.junit.Assert.assertEquals
import org.junit.Test

class StationSearchTest {
    private val stations = listOf(
        station("ST016", "Teatro Nacional", "L1", "L5"),
        station("ST020", "La Galería", "L1", "L2", "L6"),
        station("ST049", "HUB Industrial Este", "L3"),
    )

    @Test
    fun searchIgnoresCaseWhitespaceAndAccents() {
        assertEquals(listOf("ST020"), filterStations(stations, "  GALERIA ", null).map { it.code })
    }

    @Test
    fun searchAcceptsStationCodes() {
        assertEquals(listOf("ST016"), filterStations(stations, "st016", null).map { it.code })
    }

    @Test
    fun lineFilterCanBeCombinedWithText() {
        assertEquals(listOf("ST020"), filterStations(stations, "la", "L6").map { it.code })
        assertEquals(emptyList<PassengerNetworkStation>(), filterStations(stations, "teatro", "L2"))
    }

    private fun station(code: String, name: String, vararg lines: String) = PassengerNetworkStation(
        code = code,
        name = name,
        lineCodes = lines.toList(),
        active = true,
    )
}
