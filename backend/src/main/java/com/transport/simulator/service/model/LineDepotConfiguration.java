package com.transport.simulator.service.model;

public record LineDepotConfiguration(
        Long depotId,
        String depotCode,
        String depotName,
        int dispatchPriority,
        boolean dispatchEnabled,
        boolean receptionEnabled
) {
}
