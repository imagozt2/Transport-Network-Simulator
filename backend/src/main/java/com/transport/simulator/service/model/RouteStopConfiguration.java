package com.transport.simulator.service.model;

public record RouteStopConfiguration(
        Long stationId,
        String stationCode,
        String stationName,
        int stationOrder,
        Integer travelSecondsToNext,
        int dwellSeconds
) {
}
