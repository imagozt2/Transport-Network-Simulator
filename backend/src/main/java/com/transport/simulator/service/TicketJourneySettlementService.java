package com.transport.simulator.service;

import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.service.model.TicketJourneySettlement;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class TicketJourneySettlementService {

    private final NetworkJourneyPlanningService journeyPlanningService;

    public TicketJourneySettlementService(NetworkJourneyPlanningService journeyPlanningService) {
        this.journeyPlanningService = journeyPlanningService;
    }

    public TicketJourneySettlement calculate(
            Ticket ticket,
            Station entryStation,
            Station exitStation
    ) {
        Objects.requireNonNull(ticket, "ticket is required");
        Objects.requireNonNull(entryStation, "entryStation is required");
        Objects.requireNonNull(exitStation, "exitStation is required");

        int stationCount = entryStation.getCode().equals(exitStation.getCode())
                ? 1
                : journeyPlanningService.calculate(
                        entryStation.getCode(), exitStation.getCode()).stationCount();
        BigDecimal fare = switch (ticket.getProductType()) {
            case SINGLE_TRIP -> ticket.getRoutePriceAmount();
            case MULTI_TRIP -> ticket.getProduct().getPricePerTrip();
            case TIME_PASS -> BigDecimal.ZERO;
            case SMART_BALANCE -> ticket.calculateSmartBalanceFare(stationCount);
        };
        if (fare == null) {
            throw new IllegalStateException("The ticket does not define a journey fare");
        }
        return new TicketJourneySettlement(
                stationCount, fare.setScale(2, RoundingMode.HALF_UP));
    }
}
