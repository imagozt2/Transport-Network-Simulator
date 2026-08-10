package com.rmm.app.ui.screen.journeys

import com.rmm.app.core.networkcatalog.PassengerNetworkJourney
import com.rmm.app.core.networkcatalog.PassengerNetworkJourneySegment
import com.rmm.app.core.networkcatalog.PassengerNetworkJourneyStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class JourneyPresentationTest {
    @Test
    fun segmentsAndTransfersArePresentedInTravelOrder() {
        val first = segment("L6", station("ST046"), station("ST027"), "ST013")
        val second = segment("L3", station("ST027"), station("ST049"), "ST049")

        val steps = buildJourneyPresentation(journey(first, second))

        assertEquals(3, steps.size)
        assertSame(first, (steps[0] as JourneyPresentationStep.Segment).value)
        val transfer = steps[1] as JourneyPresentationStep.Transfer
        assertEquals("ST027", transfer.station.code)
        assertEquals("L6", transfer.from.lineCode)
        assertEquals("L3", transfer.to.lineCode)
        assertSame(second, (steps[2] as JourneyPresentationStep.Segment).value)
    }

    @Test
    fun segmentKeepsTheTerminalThatDefinesItsDirection() {
        val segment = segment("L1", station("ST030"), station("ST016"), "ST045")

        val step = buildJourneyPresentation(journey(segment)).single() as JourneyPresentationStep.Segment

        assertEquals("ST045", step.value.directionTerminal.code)
        assertEquals(listOf("ST030", "ST016"), step.value.stations.map { it.code })
    }

    @Test(expected = IllegalArgumentException::class)
    fun disconnectedSegmentsCannotBeRepresentedAsATransfer() {
        buildJourneyPresentation(
            journey(
                segment("L1", station("ST030"), station("ST016"), "ST045"),
                segment("L5", station("ST022"), station("ST047"), "ST047"),
            ),
        )
    }

    private fun journey(vararg segments: PassengerNetworkJourneySegment) = PassengerNetworkJourney(
        origin = segments.first().stations.first(),
        destination = segments.last().stations.last(),
        stationCount = segments.sumOf { it.stopCount },
        transferCount = segments.size - 1,
        estimatedDurationSeconds = segments.sumOf { it.travelSeconds },
        segments = segments.toList(),
    )

    private fun segment(
        lineCode: String,
        origin: PassengerNetworkJourneyStation,
        destination: PassengerNetworkJourneyStation,
        terminalCode: String,
    ) = PassengerNetworkJourneySegment(
        lineCode = lineCode,
        lineName = "Línea $lineCode",
        lineColor = "#112233",
        directionTerminal = station(terminalCode),
        stopCount = 1,
        travelSeconds = 120,
        stations = listOf(origin, destination),
    )

    private fun station(code: String) = PassengerNetworkJourneyStation(code, "Estación $code")
}
