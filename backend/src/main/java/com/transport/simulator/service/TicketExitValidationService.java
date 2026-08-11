package com.transport.simulator.service;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.enums.TicketJourneyStatus;
import com.transport.simulator.enums.TicketStatus;
import com.transport.simulator.repository.TicketJourneyRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class TicketExitValidationService {

    private final TicketJourneyRepository journeyRepository;
    private final SingleTripTicketService singleTripService;
    private final MultiTripTicketService multiTripService;
    private final TimePassTicketService timePassService;
    private final SmartBalanceTicketService smartBalanceService;

    public TicketExitValidationService(TicketJourneyRepository journeyRepository,
            SingleTripTicketService singleTripService, MultiTripTicketService multiTripService,
            TimePassTicketService timePassService, SmartBalanceTicketService smartBalanceService) {
        this.journeyRepository = journeyRepository;
        this.singleTripService = singleTripService;
        this.multiTripService = multiTripService;
        this.timePassService = timePassService;
        this.smartBalanceService = smartBalanceService;
    }

    public TicketJourney exit(Ticket ticket, String stationCode) {
        Objects.requireNonNull(ticket, "ticket is required");
        requireOpenJourney(ticket);
        requireUsableForExit(ticket);

        return switch (ticket.getProductType()) {
            case SINGLE_TRIP -> singleTripService.exit(ticket.getCode(), stationCode);
            case MULTI_TRIP -> multiTripService.exit(ticket.getCode(), stationCode);
            case TIME_PASS -> timePassService.exit(ticket.getCode(), stationCode);
            case SMART_BALANCE -> smartBalanceService.exit(ticket.getCode(), stationCode);
        };
    }

    private void requireOpenJourney(Ticket ticket) {
        if (journeyRepository.findFirstByTicketAndStatusOrderByOpenedAtDesc(
                ticket, TicketJourneyStatus.OPEN).isEmpty()) {
            throw new TicketValidationRejectionException(
                    "ENTRY_REQUIRED", "The ticket has no open journey");
        }
    }

    private void requireUsableForExit(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.BLOCKED) {
            throw new TicketValidationRejectionException("BLOCKED", "The ticket is blocked");
        }
        if (!ticket.isActive() || ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new TicketValidationRejectionException("INACTIVE", "The ticket is inactive");
        }
    }
}
