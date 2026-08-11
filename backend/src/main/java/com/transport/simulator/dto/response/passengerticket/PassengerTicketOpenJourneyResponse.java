package com.transport.simulator.dto.response.passengerticket;

import com.transport.simulator.entity.TicketJourney;
import java.time.LocalDateTime;

public record PassengerTicketOpenJourneyResponse(
        String code,
        PassengerTicketStationResponse entryStation,
        LocalDateTime openedAt
) {
    public static PassengerTicketOpenJourneyResponse from(TicketJourney journey) {
        return journey == null ? null : new PassengerTicketOpenJourneyResponse(
                journey.getCode(),
                PassengerTicketStationResponse.from(journey.getEntryStation()),
                journey.getOpenedAt()
        );
    }
}
