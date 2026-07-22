package com.transport.simulator.dto.response.trainoperation;

import com.transport.simulator.enums.ServiceOperationPhase;
import java.time.ZonedDateTime;
import java.util.List;

public record TrainOperationsResponse(
        ZonedDateTime evaluatedAt,
        ServiceOperationPhase phase,
        TrainFleetSummaryResponse summary,
        List<TrainOperationResponse> trains
) {
    public TrainOperationsResponse {
        trains = List.copyOf(trains);
    }
}
