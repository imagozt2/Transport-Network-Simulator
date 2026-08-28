package com.transport.simulator.service;

import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.enums.TicketJourneyStatus;
import com.transport.simulator.enums.TicketOperationType;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.enums.TicketStatus;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketJourneyRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.service.model.NetworkJourney;
import com.transport.simulator.service.model.TicketSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SingleTripTicketService {

    private final TicketRepository ticketRepository;
    private final TicketJourneyRepository journeyRepository;
    private final StationRepository stationRepository;
    private final NetworkJourneyPlanningService journeyPlanningService;
    private final TicketJourneySettlementService settlementService;
    private final TicketOperationRegistrationService operationRegistrationService;
    private final Clock clock;

    public SingleTripTicketService(
            TicketRepository ticketRepository,
            TicketJourneyRepository journeyRepository,
            StationRepository stationRepository,
            NetworkJourneyPlanningService journeyPlanningService,
            TicketJourneySettlementService settlementService,
            TicketOperationRegistrationService operationRegistrationService,
            Clock clock
    ) {
        this.ticketRepository = ticketRepository;
        this.journeyRepository = journeyRepository;
        this.stationRepository = stationRepository;
        this.journeyPlanningService = journeyPlanningService;
        this.settlementService = settlementService;
        this.operationRegistrationService = operationRegistrationService;
        this.clock = clock;
    }

    @Transactional
    public TicketJourney enter(String ticketCode, String stationCode) {
        Ticket ticket = requiredTicketForUpdate(ticketCode);
        Station station = requiredStation(stationCode);
        requireSingleTrip(ticket);
        requireActive(ticket);
        if (!sameStation(ticket.getOriginStation(), station)) {
            throw new IllegalArgumentException("The single ticket can only enter at its configured origin");
        }
        if (openJourney(ticket).isPresent()) {
            throw new IllegalStateException("The ticket already has an open journey");
        }

        TicketSnapshot before = TicketSnapshot.from(ticket);
        TicketJourney journey = journeyRepository.save(new TicketJourney(
                uniqueCode("RMM-JRN"), ticket, station, LocalDateTime.now(clock)
        ));
        operationRegistrationService.recordJourney(
                TicketOperationType.ENTRY_ACCEPTED, ticket, journey, station, before, BigDecimal.ZERO
        );
        return journey;
    }

    @Transactional
    public TicketJourney exit(String ticketCode, String stationCode) {
        Ticket ticket = requiredTicketForUpdate(ticketCode);
        Station station = requiredStation(stationCode);
        requireSingleTrip(ticket);
        requireActive(ticket);
        if (!sameStation(ticket.getDestinationStation(), station)) {
            throw new IllegalArgumentException("The single ticket can only exit at its configured destination");
        }

        TicketJourney journey = openJourney(ticket)
                .orElseThrow(() -> new IllegalStateException("The ticket has no open journey"));
        TicketSnapshot before = TicketSnapshot.from(ticket);
        LocalDateTime now = LocalDateTime.now(clock);
        var settlement = settlementService.calculate(ticket, journey.getEntryStation(), station);
        journey.close(station, settlement.stationCount(), settlement.fareAmount(), now);
        ticket.exhaust(now);
        TicketJourney persisted = journeyRepository.save(journey);
        operationRegistrationService.recordJourney(
                TicketOperationType.EXIT_ACCEPTED, ticket, persisted, station,
                before, settlement.fareAmount()
        );
        return persisted;
    }

    @Transactional
    public Ticket recharge(String ticketCode, String originCode, String destinationCode) {
        Ticket ticket = requiredTicketForUpdate(ticketCode);
        requireSingleTrip(ticket);
        if (ticket.getStatus() != TicketStatus.EXHAUSTED) {
            throw new IllegalStateException("Only an exhausted single ticket can be recharged");
        }
        if (openJourney(ticket).isPresent()) {
            throw new IllegalStateException("A ticket with an open journey cannot be recharged");
        }

        Station origin = requiredStation(originCode);
        Station destination = requiredStation(destinationCode);
        if (sameStation(origin, destination)) {
            throw new IllegalArgumentException("Origin and destination stations must be different");
        }
        NetworkJourney route = journeyPlanningService.calculate(origin.getCode(), destination.getCode());
        ticket.rechargeSingleTrip(origin, destination, route.stationCount(), LocalDateTime.now(clock));
        return ticket;
    }

    private Ticket requiredTicketForUpdate(String code) {
        return ticketRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
    }

    private Station requiredStation(String code) {
        return stationRepository.findByCodeAndActiveTrue(normalize(code))
                .orElseThrow(() -> new IllegalArgumentException("Active station not found"));
    }

    private java.util.Optional<TicketJourney> openJourney(Ticket ticket) {
        return journeyRepository.findFirstByTicketAndStatusOrderByOpenedAtDesc(
                ticket, TicketJourneyStatus.OPEN
        );
    }

    private void requireSingleTrip(Ticket ticket) {
        if (ticket.getProductType() != TicketProductType.SINGLE_TRIP) {
            throw new IllegalArgumentException("The operation requires a single ticket");
        }
    }

    private void requireActive(Ticket ticket) {
        if (!ticket.isActive() || ticket.getStatus() != TicketStatus.ACTIVE) {
            throw new IllegalStateException("The ticket is not available for validation");
        }
    }

    private boolean sameStation(Station first, Station second) {
        return first != null && second != null && first.getCode().equals(second.getCode());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A code is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }
}
