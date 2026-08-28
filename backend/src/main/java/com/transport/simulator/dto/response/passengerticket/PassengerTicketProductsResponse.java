package com.transport.simulator.dto.response.passengerticket;

import java.util.List;

public record PassengerTicketProductsResponse(List<PassengerTicketProductResponse> items) {
    public PassengerTicketProductsResponse {
        items = List.copyOf(items);
    }
}
