package com.transport.simulator.dto.response.stationoperation;

import com.transport.simulator.enums.ServiceOperationPhase;

public record StationOperationLineResponse(
        Long id,
        String code,
        String name,
        String color,
        int stationOrder,
        ServiceOperationPhase phase,
        boolean serviceOpen,
        int activeTrainCount,
        StationOperationTerminalResponse firstTerminal,
        StationOperationTerminalResponse lastTerminal
) {
}
