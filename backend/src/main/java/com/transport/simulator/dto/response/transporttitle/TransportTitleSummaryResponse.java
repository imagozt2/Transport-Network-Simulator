package com.transport.simulator.dto.response.transporttitle;

import com.transport.simulator.enums.TicketProductType;
import java.util.Map;

public record TransportTitleSummaryResponse(
        long totalTitles,
        long activeTitles,
        long inactiveTitles,
        Map<TicketProductType, Long> byType
) {
}
