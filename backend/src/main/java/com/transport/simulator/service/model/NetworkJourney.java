package com.transport.simulator.service.model;

import java.util.List;

public record NetworkJourney(
        Station origin,
        Station destination,
        int stationCount,
        int transferCount,
        int estimatedDurationSeconds,
        List<Station> stations,
        List<LineSegment> segments
) {
    public NetworkJourney {
        stations = List.copyOf(stations);
        segments = List.copyOf(segments);
    }

    public record Station(Long id, String code, String name) {
    }

    public record LineSegment(
            Long lineId,
            String lineCode,
            String lineName,
            String lineColor,
            Station origin,
            Station destination,
            int stopCount,
            int travelSeconds,
            List<Station> stations
    ) {
        public LineSegment {
            stations = List.copyOf(stations);
        }
    }
}
