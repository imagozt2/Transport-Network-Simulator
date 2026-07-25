package com.transport.simulator.dto.response.lineoperation;

public record LineOperationDepotResponse(
        Long id,
        String code,
        String name,
        LineOperationDepotStationResponse station,
        LineOperationDepotStationResponse dispatchTerminal,
        int dispatchPriority,
        boolean dispatchEnabled,
        boolean receptionEnabled,
        long assignedTrainCount,
        long trainsInService,
        long availableTrainCount
) {
}
