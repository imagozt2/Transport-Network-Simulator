package com.transport.simulator.dto.response.stationoperation;

import com.transport.simulator.enums.ServiceDirection;
import java.time.ZonedDateTime;

public record StationArrivalResponse(
        Long trainId,
        String trainCode,
        String trainSeries,
        Long lineId,
        String lineCode,
        String lineName,
        String lineColor,
        ServiceDirection direction,
        StationOperationTerminalResponse destination,
        int stationsAway,
        long secondsUntilArrival,
        ZonedDateTime estimatedArrivalAt,
        boolean atStation
) {
}
