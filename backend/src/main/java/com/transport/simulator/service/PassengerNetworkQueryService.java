package com.transport.simulator.service;

import com.transport.simulator.dto.response.networkmap.NetworkMapLineResponse;
import com.transport.simulator.dto.response.networkmap.NetworkMapResponse;
import com.transport.simulator.dto.response.networkmap.NetworkMapStationResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkLineResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkLinesResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkJourneyResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkJourneySegmentResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkJourneyStationResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkStationResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkStationsResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.transport.simulator.service.model.NetworkJourney;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PassengerNetworkQueryService {

    private final NetworkMapQueryService networkMapQueryService;
    private final NetworkJourneyPlanningService journeyPlanningService;

    public PassengerNetworkQueryService(
            NetworkMapQueryService networkMapQueryService,
            NetworkJourneyPlanningService journeyPlanningService
    ) {
        this.networkMapQueryService = networkMapQueryService;
        this.journeyPlanningService = journeyPlanningService;
    }

    public PassengerNetworkLinesResponse lines() {
        List<PassengerNetworkLineResponse> lines = networkMapQueryService.getNetworkMap().lines()
                .stream()
                .map(this::toLine)
                .toList();
        return new PassengerNetworkLinesResponse(lines);
    }

    public PassengerNetworkStationsResponse stations(String query, String lineCode) {
        String normalizedQuery = normalize(query);
        String normalizedLineCode = normalize(lineCode);
        Map<String, StationAccumulator> stations = aggregateStations(
                networkMapQueryService.getNetworkMap()
        );

        List<PassengerNetworkStationResponse> matches = stations.values().stream()
                .map(StationAccumulator::response)
                .filter(station -> normalizedQuery.isEmpty()
                        || normalize(station.name()).contains(normalizedQuery)
                        || normalize(station.code()).contains(normalizedQuery))
                .filter(station -> normalizedLineCode.isEmpty()
                        || station.lineCodes().stream()
                        .map(this::normalize)
                        .anyMatch(normalizedLineCode::equals))
                .sorted((left, right) -> left.name().compareToIgnoreCase(right.name()))
                .toList();

        return new PassengerNetworkStationsResponse(matches);
    }

    public PassengerNetworkJourneyResponse journey(String originCode, String destinationCode) {
        NetworkJourney journey = journeyPlanningService.calculate(originCode, destinationCode);
        Map<String, NetworkMapLineResponse> linesByCode = networkMapQueryService.getNetworkMap()
                .lines().stream()
                .collect(java.util.stream.Collectors.toMap(
                        NetworkMapLineResponse::code,
                        line -> line
                ));

        return new PassengerNetworkJourneyResponse(
                toJourneyStation(journey.origin()),
                toJourneyStation(journey.destination()),
                journey.stationCount(),
                journey.transferCount(),
                journey.estimatedDurationSeconds(),
                journey.segments().stream()
                        .map(segment -> toJourneySegment(segment, linesByCode.get(segment.lineCode())))
                        .toList()
        );
    }

    private PassengerNetworkJourneySegmentResponse toJourneySegment(
            NetworkJourney.LineSegment segment,
            NetworkMapLineResponse line
    ) {
        if (line == null) {
            throw new ServiceConfigurationException(
                    "Missing active line " + segment.lineCode() + " while presenting journey"
            );
        }
        List<String> route = line.stations().stream().map(NetworkMapStationResponse::code).toList();
        int originIndex = route.indexOf(segment.origin().code());
        int destinationIndex = route.indexOf(segment.destination().code());
        if (originIndex < 0 || destinationIndex < 0 || route.isEmpty()) {
            throw new ServiceConfigurationException(
                    "Journey segment does not match line " + segment.lineCode()
            );
        }
        NetworkMapStationResponse terminal = destinationIndex >= originIndex
                ? line.stations().getLast()
                : line.stations().getFirst();

        return new PassengerNetworkJourneySegmentResponse(
                segment.lineCode(),
                segment.lineName(),
                segment.lineColor(),
                new PassengerNetworkJourneyStationResponse(terminal.code(), terminal.name()),
                segment.stopCount(),
                segment.travelSeconds(),
                segment.stations().stream().map(this::toJourneyStation).toList()
        );
    }

    private PassengerNetworkJourneyStationResponse toJourneyStation(NetworkJourney.Station station) {
        return new PassengerNetworkJourneyStationResponse(station.code(), station.name());
    }

    private PassengerNetworkLineResponse toLine(NetworkMapLineResponse line) {
        List<String> terminals = line.stations().isEmpty()
                ? List.of()
                : List.of(
                        line.stations().getFirst().code(),
                        line.stations().getLast().code()
                );
        return new PassengerNetworkLineResponse(
                line.code(), line.name(), line.color(), terminals, true
        );
    }

    private Map<String, StationAccumulator> aggregateStations(NetworkMapResponse network) {
        Map<String, StationAccumulator> stations = new LinkedHashMap<>();
        for (NetworkMapLineResponse line : network.lines()) {
            for (NetworkMapStationResponse station : line.stations()) {
                stations.computeIfAbsent(
                        station.code(),
                        ignored -> new StationAccumulator(station.code(), station.name())
                ).addLine(line.code());
            }
        }
        return stations;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class StationAccumulator {
        private final String code;
        private final String name;
        private final java.util.ArrayList<String> lineCodes = new java.util.ArrayList<>();

        private StationAccumulator(String code, String name) {
            this.code = code;
            this.name = name;
        }

        private void addLine(String lineCode) {
            if (!lineCodes.contains(lineCode)) {
                lineCodes.add(lineCode);
            }
        }

        private PassengerNetworkStationResponse response() {
            return new PassengerNetworkStationResponse(
                    code, name, List.copyOf(lineCodes), true
            );
        }
    }
}
