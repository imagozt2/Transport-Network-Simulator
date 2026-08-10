package com.transport.simulator.dto.response.passengernetwork;

import java.util.List;

public record PassengerNetworkStationResponse(
        String code,
        String name,
        List<String> lineCodes,
        boolean active
) {
}
