package com.transport.simulator.dto.response.passengerticket;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.enums.TicketSupportType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PassengerTicketDetailResponse(
        String code,
        PassengerTicketProductSummaryResponse product,
        TicketSupportType medium,
        String status,
        PassengerTicketStationResponse originStation,
        PassengerTicketStationResponse destinationStation,
        Integer stationCount,
        BigDecimal routePriceAmount,
        Integer purchasedTrips,
        Integer remainingTrips,
        Integer purchasedDays,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        BigDecimal balanceAmount,
        String currency,
        PassengerTicketOpenJourneyResponse openJourney,
        LocalDateTime issuedAt,
        LocalDateTime lastRechargedAt,
        LocalDateTime lastUsedAt
) {
    public static PassengerTicketDetailResponse from(
            Ticket ticket,
            TicketSupportType medium,
            TicketJourney openJourney
    ) {
        return new PassengerTicketDetailResponse(
                ticket.getCode(), PassengerTicketProductSummaryResponse.from(ticket), medium,
                ticket.getStatus().name(),
                PassengerTicketStationResponse.from(ticket.getOriginStation()),
                PassengerTicketStationResponse.from(ticket.getDestinationStation()),
                ticket.getStationCount(), ticket.getRoutePriceAmount(), ticket.getPurchasedTrips(),
                ticket.getRemainingTrips(), ticket.getPurchasedDays(), ticket.getValidFrom(),
                ticket.getValidUntil(), ticket.getBalanceAmount(), ticket.getCurrency(),
                PassengerTicketOpenJourneyResponse.from(openJourney), ticket.getIssuedAt(),
                ticket.getLastRechargedAt(), ticket.getLastUsedAt()
        );
    }
}
