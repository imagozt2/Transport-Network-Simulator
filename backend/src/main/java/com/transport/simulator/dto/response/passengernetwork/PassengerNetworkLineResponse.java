package com.transport.simulator.dto.response.passengernetwork;

import java.util.List;

public record PassengerNetworkLineResponse(
        String code,
        String name,
        String color,
        List<String> terminals,
        boolean active
) {
}
