package com.transport.simulator.service;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.enums.TicketStatus;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketEntryValidationService {

    private final SingleTripTicketService singleTripService;
    private final MultiTripTicketService multiTripService;
    private final TimePassTicketService timePassService;
    private final SmartBalanceTicketService smartBalanceService;
    private final TicketJourneyAnomalyService anomalyService;

    public TicketEntryValidationService(SingleTripTicketService singleTripService,
            MultiTripTicketService multiTripService,
            TimePassTicketService timePassService, SmartBalanceTicketService smartBalanceService,
            TicketJourneyAnomalyService anomalyService) {
        this.singleTripService = singleTripService;
        this.multiTripService = multiTripService;
        this.timePassService = timePassService;
        this.smartBalanceService = smartBalanceService;
        this.anomalyService = anomalyService;
    }

    @Transactional
    public TicketJourney enter(Ticket ticket, String stationCode) {
        Objects.requireNonNull(ticket, "ticket is required");
        requireUsableForEntry(ticket);
        anomalyService.forceCloseJourneyWithoutExit(ticket);

        return switch (ticket.getProductType()) {
            case SINGLE_TRIP -> singleTripService.enter(ticket.getCode(), stationCode);
            case MULTI_TRIP -> multiTripService.enter(ticket.getCode(), stationCode);
            case TIME_PASS -> timePassService.enter(ticket.getCode(), stationCode);
            case SMART_BALANCE -> smartBalanceService.enter(ticket.getCode(), stationCode);
        };
    }

    private void requireUsableForEntry(Ticket ticket) {
        if (!ticket.isActive() || ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new TicketValidationRejectionException("INACTIVE", "The ticket is inactive");
        }
        if (ticket.getStatus() == TicketStatus.BLOCKED) {
            throw new TicketValidationRejectionException("BLOCKED", "The ticket is blocked");
        }
        if (ticket.getStatus() == TicketStatus.EXPIRED) {
            throw new TicketValidationRejectionException("EXPIRED", "The ticket has expired");
        }
        if (ticket.getStatus() == TicketStatus.EXHAUSTED) {
            throw new TicketValidationRejectionException("EXHAUSTED", "The ticket is exhausted");
        }
    }
}
