package com.transport.simulator.controller;

import com.transport.simulator.dto.response.networkjourney.NetworkJourneyResponse;
import com.transport.simulator.dto.response.networkjourney.NetworkJourneySegmentResponse;
import com.transport.simulator.dto.response.networkjourney.NetworkJourneyStationResponse;
import com.transport.simulator.service.NetworkJourneyPlanningService;
import com.transport.simulator.service.ServiceConfigurationException;
import com.transport.simulator.service.model.NetworkJourney;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/network-map/journeys")
public class NetworkJourneyController {

    private final NetworkJourneyPlanningService journeyPlanningService;

    public NetworkJourneyController(NetworkJourneyPlanningService journeyPlanningService) {
        this.journeyPlanningService = journeyPlanningService;
    }

    @GetMapping
    public NetworkJourneyResponse calculateJourney(
            @RequestParam String originStationCode,
            @RequestParam String destinationStationCode
    ) {
        try {
            return toResponse(journeyPlanningService.calculate(originStationCode, destinationStationCode));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (ServiceConfigurationException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        }
    }

    private NetworkJourneyResponse toResponse(NetworkJourney journey) {
        return new NetworkJourneyResponse(
                toStationResponse(journey.origin()),
                toStationResponse(journey.destination()),
                journey.stationCount(),
                journey.transferCount(),
                journey.estimatedDurationSeconds(),
                journey.stations().stream().map(this::toStationResponse).toList(),
                journey.segments().stream().map(this::toSegmentResponse).toList()
        );
    }

    private NetworkJourneySegmentResponse toSegmentResponse(NetworkJourney.LineSegment segment) {
        return new NetworkJourneySegmentResponse(
                segment.lineId(),
                segment.lineCode(),
                segment.lineName(),
                segment.lineColor(),
                toStationResponse(segment.origin()),
                toStationResponse(segment.destination()),
                segment.stopCount(),
                segment.travelSeconds(),
                segment.stations().stream().map(this::toStationResponse).toList()
        );
    }

    private NetworkJourneyStationResponse toStationResponse(NetworkJourney.Station station) {
        return new NetworkJourneyStationResponse(station.id(), station.code(), station.name());
    }
}
