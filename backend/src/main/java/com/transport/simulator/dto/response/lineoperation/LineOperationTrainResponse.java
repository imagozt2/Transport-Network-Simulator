package com.transport.simulator.dto.response.lineoperation;

import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.TrainPositionState;
import java.time.ZonedDateTime;

public record LineOperationTrainResponse(
        Long id,
        String code,
        String series,
        int dutyNumber,
        TrainPositionState positionState,
        ServiceDirection direction,
        Long currentStationId,
        String currentStationCode,
        Long previousStationId,
        String previousStationCode,
        Long nextStationId,
        String nextStationCode,
        int progressPercentage,
        long secondsUntilNextStation,
        ZonedDateTime estimatedArrivalAt
) {
}
