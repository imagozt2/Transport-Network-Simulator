package com.transport.simulator.dto.response.stationoperation;

import com.transport.simulator.enums.ServiceDirection;

public record StationOperationDirectionResponse(
        ServiceDirection direction,
        StationOperationTerminalResponse destination,
        int activeTrainCount
) {
}
