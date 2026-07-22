package com.transport.simulator.dto.response.depotoperation;

import com.transport.simulator.enums.ServiceOperationPhase;
import java.time.ZonedDateTime;
import java.util.List;

public record DepotOperationsResponse(
        ZonedDateTime evaluatedAt,
        ServiceOperationPhase phase,
        DepotOperationsSummaryResponse summary,
        List<DepotOperationResponse> depots
) {
    public DepotOperationsResponse {
        depots = List.copyOf(depots);
    }
}
