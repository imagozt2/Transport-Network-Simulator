package com.transport.simulator.dto.response.lineoperation;

import com.transport.simulator.enums.ServiceOperationPhase;
import java.time.ZonedDateTime;
import java.util.List;

public record LineOperationsResponse(
        ZonedDateTime evaluatedAt,
        ServiceOperationPhase phase,
        int activeLineCount,
        List<LineOperationResponse> lines
) {
}
