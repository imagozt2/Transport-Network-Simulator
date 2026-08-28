package com.transport.simulator.dto.response.passengerticket;

import com.transport.simulator.entity.Station;

public record PassengerTicketStationResponse(String code, String name) {
    public static PassengerTicketStationResponse from(Station station) {
        return station == null ? null : new PassengerTicketStationResponse(
                station.getCode(), station.getName()
        );
    }
}
