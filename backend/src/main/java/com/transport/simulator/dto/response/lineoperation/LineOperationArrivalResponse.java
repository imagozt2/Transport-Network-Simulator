package com.transport.simulator.dto.response.lineoperation;

import com.transport.simulator.enums.ServiceDirection;
import java.time.ZonedDateTime;

public record LineOperationArrivalResponse(
        Long stationId,
        String stationCode,
        String stationName,
        Long trainId,
        String trainCode,
        String trainSeries,
        ServiceDirection direction,
        Long destinationStationId,
        String destinationStationCode,
        String destinationStationName,
        int stationsAway,
        long secondsUntilArrival,
        ZonedDateTime estimatedArrivalAt,
        boolean atStation
) {
}
