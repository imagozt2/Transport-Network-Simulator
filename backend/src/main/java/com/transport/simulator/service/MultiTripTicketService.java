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
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MultiTripTicketService {

    private final TicketRepository ticketRepository;
    private final TicketJourneyRepository journeyRepository;
    private final StationRepository stationRepository;
    private final NetworkJourneyPlanningService journeyPlanningService;
    private final TicketOperationRegistrationService operationRegistrationService;
    private final Clock clock;

    public MultiTripTicketService(
            TicketRepository ticketRepository,
            TicketJourneyRepository journeyRepository,
            StationRepository stationRepository,
            NetworkJourneyPlanningService journeyPlanningService,
            TicketOperationRegistrationService operationRegistrationService,
            Clock clock
    ) {
        this.ticketRepository = ticketRepository;
        this.journeyRepository = journeyRepository;
        this.stationRepository = stationRepository;
        this.journeyPlanningService = journeyPlanningService;
        this.operationRegistrationService = operationRegistrationService;
        this.clock = clock;
    }

    @Transactional
    public TicketJourney enter(String ticketCode, String stationCode) {
        Ticket ticket = requiredTicketForUpdate(ticketCode);
        Station station = requiredStation(stationCode);
        requireMultiTrip(ticket);
        requireAvailableForEntry(ticket);
        if (openJourney(ticket).isPresent()) {
            throw new IllegalStateException("The ticket already has an open journey");
        }

        TicketSnapshot before = TicketSnapshot.from(ticket);
        LocalDateTime now = LocalDateTime.now(clock);
        ticket.consumeTrip(now);
        TicketJourney journey = journeyRepository.save(new TicketJourney(
                uniqueCode("RMM-JRN"), ticket, station, now
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
        requireMultiTrip(ticket);
        if (!ticket.isActive() || (ticket.getStatus() != TicketStatus.ACTIVE
                && ticket.getStatus() != TicketStatus.EXHAUSTED)) {
            throw new IllegalStateException("The ticket cannot complete its open journey");
        }

        TicketSnapshot before = TicketSnapshot.from(ticket);
        TicketJourney journey = openJourney(ticket)
                .orElseThrow(() -> new IllegalStateException("The ticket has no open journey"));
        int stationCount = calculateStationCount(journey.getEntryStation(), station);
        journey.close(
                station,
                stationCount,
                ticket.getProduct().getPricePerTrip(),
                LocalDateTime.now(clock)
        );
        TicketJourney persisted = journeyRepository.save(journey);
        operationRegistrationService.recordJourney(
                TicketOperationType.EXIT_ACCEPTED, ticket, persisted, station,
                before, ticket.getProduct().getPricePerTrip()
        );
        return persisted;
    }

    @Transactional
    public Ticket recharge(String ticketCode, int trips) {
        Ticket ticket = requiredTicketForUpdate(ticketCode);
        requireMultiTrip(ticket);
        if (openJourney(ticket).isPresent()) {
            throw new IllegalStateException("A ticket with an open journey cannot be recharged");
        }
        ticket.rechargeTrips(trips, LocalDateTime.now(clock));
        return ticket;
    }

    private int calculateStationCount(Station origin, Station destination) {
        if (origin.getCode().equals(destination.getCode())) {
            return 1;
        }
        NetworkJourney route = journeyPlanningService.calculate(origin.getCode(), destination.getCode());
        return route.stationCount();
    }

    private Ticket requiredTicketForUpdate(String code) {
        return ticketRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
    }

    private Station requiredStation(String code) {
        return stationRepository.findByCodeAndActiveTrue(normalize(code))
                .orElseThrow(() -> new IllegalArgumentException("Active station not found"));
    }

    private Optional<TicketJourney> openJourney(Ticket ticket) {
        return journeyRepository.findFirstByTicketAndStatusOrderByOpenedAtDesc(
                ticket, TicketJourneyStatus.OPEN
        );
    }

    private void requireMultiTrip(Ticket ticket) {
        if (ticket.getProductType() != TicketProductType.MULTI_TRIP) {
            throw new IllegalArgumentException("The operation requires a multi-trip ticket");
        }
    }

    private void requireAvailableForEntry(Ticket ticket) {
        if (!ticket.isActive() || ticket.getStatus() != TicketStatus.ACTIVE
                || ticket.getRemainingTrips() == null || ticket.getRemainingTrips() <= 0) {
            throw new IllegalStateException("The ticket has no trips available");
        }
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
