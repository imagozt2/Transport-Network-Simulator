package com.transport.simulator.dto.response.networkmap;

import java.util.List;

public record NetworkMapLineResponse(
        Long id,
        String code,
        String name,
        String color,
        List<NetworkMapStationResponse> stations
) {
}
