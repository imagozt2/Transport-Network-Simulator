package com.transport.simulator.service;

import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.enums.TicketJourneyStatus;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.enums.TicketStatus;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketJourneyRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.service.model.NetworkJourney;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimePassTicketService {

    private final TicketRepository ticketRepository;
    private final TicketJourneyRepository journeyRepository;
    private final StationRepository stationRepository;
    private final NetworkJourneyPlanningService journeyPlanningService;
    private final Clock clock;

    public TimePassTicketService(
            TicketRepository ticketRepository,
            TicketJourneyRepository journeyRepository,
            StationRepository stationRepository,
            NetworkJourneyPlanningService journeyPlanningService,
            Clock clock
    ) {
        this.ticketRepository = ticketRepository;
        this.journeyRepository = journeyRepository;
        this.stationRepository = stationRepository;
        this.journeyPlanningService = journeyPlanningService;
        this.clock = clock;
    }

    @Transactional
    public TicketJourney enter(String ticketCode, String stationCode) {
        Ticket ticket = requiredTicketForUpdate(ticketCode);
        Station station = requiredStation(stationCode);
        requireTimePass(ticket);
        LocalDateTime now = LocalDateTime.now(clock);
        ticket.refreshTimePassStatus(now);
        if (!ticket.isValidAt(now)) {
            throw new IllegalStateException("The time pass is outside its validity period");
        }
        if (openJourney(ticket).isPresent()) {
            throw new IllegalStateException("The ticket already has an open journey");
        }

        ticket.recordUse(now);
        return journeyRepository.save(new TicketJourney(
                uniqueCode("RMM-JRN"), ticket, station, now
        ));
    }

    @Transactional
    public TicketJourney exit(String ticketCode, String stationCode) {
        Ticket ticket = requiredTicketForUpdate(ticketCode);
        Station station = requiredStation(stationCode);
        requireTimePass(ticket);
        if (!ticket.isActive() || ticket.getStatus() == TicketStatus.BLOCKED
                || ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new IllegalStateException("The ticket cannot complete its open journey");
        }

        TicketJourney journey = openJourney(ticket)
                .orElseThrow(() -> new IllegalStateException("The ticket has no open journey"));
        LocalDateTime now = LocalDateTime.now(clock);
        ticket.refreshTimePassStatus(now);
        int stationCount = calculateStationCount(journey.getEntryStation(), station);
        journey.close(station, stationCount, BigDecimal.ZERO, now);
        ticket.recordUse(now);
        return journeyRepository.save(journey);
    }

    @Transactional
    public Ticket renew(String ticketCode, int days) {
        Ticket ticket = requiredTicketForUpdate(ticketCode);
        requireTimePass(ticket);
        if (openJourney(ticket).isPresent()) {
            throw new IllegalStateException("A ticket with an open journey cannot be renewed");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ticket.refreshTimePassStatus(now);
        ticket.renewValidity(days, now);
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

    private void requireTimePass(Ticket ticket) {
        if (ticket.getProductType() != TicketProductType.TIME_PASS) {
            throw new IllegalArgumentException("The operation requires a time pass");
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
