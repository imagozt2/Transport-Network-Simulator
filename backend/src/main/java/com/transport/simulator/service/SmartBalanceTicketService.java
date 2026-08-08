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
public class SmartBalanceTicketService {

    private final TicketRepository ticketRepository;
    private final TicketJourneyRepository journeyRepository;
    private final StationRepository stationRepository;
    private final NetworkJourneyPlanningService journeyPlanningService;
    private final Clock clock;

    public SmartBalanceTicketService(
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
        requireSmartBalance(ticket);
        if (!ticket.canStartSmartBalanceJourney()) {
            throw new IllegalStateException("The ticket has insufficient balance for entry");
        }
        if (openJourney(ticket).isPresent()) {
            throw new IllegalStateException("The ticket already has an open journey");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ticket.recordUse(now);
        return journeyRepository.save(new TicketJourney(
                uniqueCode("RMM-JRN"), ticket, station, now
        ));
    }

    @Transactional
    public TicketJourney exit(String ticketCode, String stationCode) {
        Ticket ticket = requiredTicketForUpdate(ticketCode);
        Station station = requiredStation(stationCode);
        requireSmartBalance(ticket);
        if (!ticket.isActive() || ticket.getStatus() != TicketStatus.ACTIVE) {
            throw new IllegalStateException("The ticket cannot complete its open journey");
        }

        TicketJourney journey = openJourney(ticket)
                .orElseThrow(() -> new IllegalStateException("The ticket has no open journey"));
        int stationCount = calculateStationCount(journey.getEntryStation(), station);
        BigDecimal fare = ticket.calculateSmartBalanceFare(stationCount);
        LocalDateTime now = LocalDateTime.now(clock);

        // El saldo se modifica antes de cerrar el trayecto. Si no es suficiente, la excepción
        // conserva ambas operaciones sin cambios gracias a la transacción.
        ticket.deductSmartBalanceFare(fare, now);
        journey.close(station, stationCount, fare, now);
        return journeyRepository.save(journey);
    }

    @Transactional
    public Ticket recharge(String ticketCode, BigDecimal amount) {
        Ticket ticket = requiredTicketForUpdate(ticketCode);
        requireSmartBalance(ticket);
        // Se admite recargar durante un trayecto para que un saldo insuficiente en la salida
        // pueda regularizarse sin cancelar ni duplicar el viaje abierto.
        ticket.rechargeMoneyBalance(amount, LocalDateTime.now(clock));
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

    private void requireSmartBalance(Ticket ticket) {
        if (ticket.getProductType() != TicketProductType.SMART_BALANCE) {
            throw new IllegalArgumentException("The operation requires a smart-balance ticket");
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
