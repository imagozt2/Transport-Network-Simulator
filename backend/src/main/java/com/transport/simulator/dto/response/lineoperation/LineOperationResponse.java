package com.transport.simulator.dto.response.lineoperation;

import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.ServicePeriodType;
import java.time.ZonedDateTime;
import java.util.List;

public record LineOperationResponse(
        Long id,
        String code,
        String name,
        String color,
        ServiceOperationPhase phase,
        boolean serviceOpen,
        ZonedDateTime serviceStartsAt,
        ZonedDateTime serviceEndsAt,
        String currentPeriodCode,
        ServicePeriodType currentPeriodType,
        Integer headwaySeconds,
        Long estimatedOneWayDurationSeconds,
        int stationCount,
        LineOperationStationResponse firstTerminal,
        LineOperationStationResponse lastTerminal,
        int activeTrainCount,
        List<LineOperationStationResponse> stations,
        List<LineOperationTrainResponse> trains
) {
}
