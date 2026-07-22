package com.transport.simulator.dto.response.depotoperation;

import java.time.ZonedDateTime;

public record DepotMovementsSummaryResponse(
        int total,
        long exits,
        long entries,
        long completed,
        long scheduled,
        ZonedDateTime nextMovementAt
) {
}
