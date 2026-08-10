package com.transport.simulator.service;

import com.transport.simulator.dto.response.networkmap.NetworkMapLineResponse;
import com.transport.simulator.dto.response.networkmap.NetworkMapResponse;
import com.transport.simulator.dto.response.networkmap.NetworkMapStationResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkLineResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkLinesResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkStationResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkStationsResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PassengerNetworkQueryService {

    private final NetworkMapQueryService networkMapQueryService;

    public PassengerNetworkQueryService(NetworkMapQueryService networkMapQueryService) {
        this.networkMapQueryService = networkMapQueryService;
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
