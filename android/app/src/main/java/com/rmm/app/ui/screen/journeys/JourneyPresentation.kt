package com.rmm.app.ui.screen.journeys

import com.rmm.app.core.networkcatalog.PassengerNetworkJourney
import com.rmm.app.core.networkcatalog.PassengerNetworkJourneySegment
import com.rmm.app.core.networkcatalog.PassengerNetworkJourneyStation

internal sealed interface JourneyPresentationStep {
    val key: String

    data class Segment(
        val index: Int,
        val value: PassengerNetworkJourneySegment,
    ) : JourneyPresentationStep {
        override val key = "segment-$index-${value.lineCode}"
    }

    data class Transfer(
        val index: Int,
        val station: PassengerNetworkJourneyStation,
        val from: PassengerNetworkJourneySegment,
        val to: PassengerNetworkJourneySegment,
    ) : JourneyPresentationStep {
        override val key = "transfer-$index-${station.code}-${from.lineCode}-${to.lineCode}"
    }
}

internal fun buildJourneyPresentation(
    journey: PassengerNetworkJourney,
): List<JourneyPresentationStep> = buildList {
    journey.segments.forEachIndexed { index, segment ->
        add(JourneyPresentationStep.Segment(index, segment))
        if (index < journey.segments.lastIndex) {
            val next = journey.segments[index + 1]
            val transferStation = segment.stations.lastOrNull()
            require(transferStation != null && transferStation.code == next.stations.firstOrNull()?.code) {
                "Los tramos consecutivos deben compartir la estación de transbordo"
            }
            add(JourneyPresentationStep.Transfer(index, transferStation, segment, next))
        }
    }
}
