package com.transport.simulator.dto.response.transporttitle;

import java.util.List;

public record TransportTitlesResponse(
        String currency,
        TransportTitleSummaryResponse summary,
        List<TransportTitleResponse> titles
) {
}
