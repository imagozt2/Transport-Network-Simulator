package com.transport.simulator.dto.response.trainoperation;

import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.TrainPositionState;
import java.time.ZonedDateTime;

public record TrainServiceLocationResponse(
        TrainOperationLineResponse currentLine,
        int dutyNumber,
        TrainPositionState positionState,
        ServiceDirection direction,
        TrainOperationStationResponse destination,
        TrainOperationStationResponse currentStation,
        TrainOperationStationResponse previousStation,
        TrainOperationStationResponse nextStation,
        int progressPercentage,
        long secondsUntilNextStation,
        ZonedDateTime estimatedArrivalAt
) {
}
