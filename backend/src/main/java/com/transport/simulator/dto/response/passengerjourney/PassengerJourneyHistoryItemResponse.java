package com.transport.simulator.dto.response.passengerjourney;

import com.transport.simulator.dto.response.passengerticket.PassengerTicketStationResponse;
import com.transport.simulator.entity.TicketJourney;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PassengerJourneyHistoryItemResponse(
        String code,
        String ticketCode,
        String productName,
        String productType,
        PassengerTicketStationResponse origin,
        PassengerTicketStationResponse destination,
        String status,
        Integer stationCount,
        BigDecimal fareAmount,
        String currency,
        LocalDateTime openedAt,
        LocalDateTime endedAt,
        Integer durationSeconds,
        boolean anomalous
) {
    public static PassengerJourneyHistoryItemResponse from(TicketJourney journey) {
        LocalDateTime endedAt = journey.getClosedAt() != null
                ? journey.getClosedAt()
                : journey.getForcedClosedAt();
        return new PassengerJourneyHistoryItemResponse(
                journey.getCode(),
                journey.getTicket().getCode(),
                journey.getTicket().getProduct().getName(),
                journey.getTicket().getProductType().name(),
                PassengerTicketStationResponse.from(journey.getEntryStation()),
                PassengerTicketStationResponse.from(journey.getExitStation()),
                journey.getStatus().name(),
                journey.getStationCount(),
                journey.getFareAmount(),
                journey.getCurrency(),
                journey.getOpenedAt(),
                endedAt,
                journey.getDurationSeconds(),
                journey.isAnomalous()
        );
    }
}
