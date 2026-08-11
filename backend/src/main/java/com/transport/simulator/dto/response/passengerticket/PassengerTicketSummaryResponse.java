package com.transport.simulator.dto.response.passengerticket;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.enums.TicketSupportType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PassengerTicketSummaryResponse(
        String code,
        PassengerTicketProductSummaryResponse product,
        TicketSupportType medium,
        String status,
        PassengerTicketStationResponse originStation,
        PassengerTicketStationResponse destinationStation,
        Integer stationCount,
        Integer remainingTrips,
        Integer purchasedDays,
        BigDecimal balanceAmount,
        String currency,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        boolean openJourney,
        LocalDateTime issuedAt
) {
    public static PassengerTicketSummaryResponse from(
            Ticket ticket,
            TicketSupportType medium,
            boolean openJourney
    ) {
        return new PassengerTicketSummaryResponse(
                ticket.getCode(), PassengerTicketProductSummaryResponse.from(ticket), medium,
                ticket.getStatus().name(),
                PassengerTicketStationResponse.from(ticket.getOriginStation()),
                PassengerTicketStationResponse.from(ticket.getDestinationStation()),
                ticket.getStationCount(), ticket.getRemainingTrips(), ticket.getPurchasedDays(),
                ticket.getBalanceAmount(), ticket.getCurrency(), ticket.getValidFrom(),
                ticket.getValidUntil(), openJourney, ticket.getIssuedAt()
        );
    }
}
