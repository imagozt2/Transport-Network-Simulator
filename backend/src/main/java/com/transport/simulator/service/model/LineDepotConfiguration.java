package com.transport.simulator.service.model;

public record LineDepotConfiguration(
        Long depotId,
        String depotCode,
        String depotName,
        Long stationId,
        String stationCode,
        Long dispatchTerminalStationId,
        String dispatchTerminalStationCode,
        int dispatchPriority,
        boolean dispatchEnabled,
        boolean receptionEnabled
) {
}
