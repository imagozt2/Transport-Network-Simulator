package com.transport.simulator.service;

import com.transport.simulator.dto.response.passengerticket.PassengerTicketDetailResponse;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketSummaryResponse;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketsResponse;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.entity.TicketSupport;
import com.transport.simulator.enums.TicketJourneyStatus;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.enums.TicketStatus;
import com.transport.simulator.enums.TicketSupportStatus;
import com.transport.simulator.enums.TicketSupportType;
import com.transport.simulator.repository.TicketJourneyRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.repository.TicketSupportRepository;
import com.transport.simulator.security.PassengerPrincipal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PassengerTicketQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final PassengerResourceAccessService accessService;
    private final TicketRepository ticketRepository;
    private final TicketSupportRepository supportRepository;
    private final TicketJourneyRepository journeyRepository;

    public PassengerTicketQueryService(
            PassengerResourceAccessService accessService,
            TicketRepository ticketRepository,
            TicketSupportRepository supportRepository,
            TicketJourneyRepository journeyRepository
    ) {
        this.accessService = accessService;
        this.ticketRepository = ticketRepository;
        this.supportRepository = supportRepository;
        this.journeyRepository = journeyRepository;
    }

    @Transactional(readOnly = true)
    public PassengerTicketsResponse tickets(
            String status,
            String productType,
            Integer limit,
            String cursor,
            Authentication authentication
    ) {
        PassengerPrincipal principal = accessService.requirePassenger(authentication);
        TicketStatus parsedStatus = enumValue(status, TicketStatus.class, "ticket status");
        TicketProductType parsedProductType = enumValue(
                productType, TicketProductType.class, "ticket product type"
        );
        int safeLimit = normalizedLimit(limit);
        Ticket cursorTicket = cursor == null || cursor.isBlank()
                ? null
                : accessService.ownedTicket(cursor, authentication);

        List<Ticket> result = ticketRepository.findPassengerWallet(
                principal.accountId(), parsedStatus, parsedProductType,
                cursorTicket == null ? null : cursorTicket.getIssuedAt(),
                cursorTicket == null ? null : cursorTicket.getId(),
                PageRequest.of(0, safeLimit + 1)
        );
        boolean hasNext = result.size() > safeLimit;
        List<Ticket> page = hasNext ? result.subList(0, safeLimit) : result;
        WalletRelations relations = relations(page);
        List<PassengerTicketSummaryResponse> items = page.stream()
                .map(ticket -> PassengerTicketSummaryResponse.from(
                        ticket,
                        relations.mediumByTicketId().get(ticket.getId()),
                        relations.openTicketIds().contains(ticket.getId())
                ))
                .toList();
        String nextCursor = hasNext && !page.isEmpty() ? page.getLast().getCode() : null;
        return new PassengerTicketsResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public PassengerTicketDetailResponse ticket(String code, Authentication authentication) {
        Ticket ticket = accessService.ownedTicket(code, authentication);
        TicketSupportType medium = supportRepository
                .findFirstByTicketIdAndStatusOrderByIssuedAtDesc(
                        ticket.getId(), TicketSupportStatus.ACTIVE
                )
                .map(TicketSupport::getType)
                .orElse(null);
        TicketJourney openJourney = journeyRepository
                .findFirstByTicketAndStatusOrderByOpenedAtDesc(ticket, TicketJourneyStatus.OPEN)
                .orElse(null);
        return PassengerTicketDetailResponse.from(ticket, medium, openJourney);
    }

    private WalletRelations relations(List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            return new WalletRelations(Map.of(), Set.of());
        }
        List<Long> ticketIds = tickets.stream().map(Ticket::getId).toList();
        Map<Long, TicketSupportType> mediumByTicketId = new HashMap<>();
        supportRepository.findAllByTicketIdInAndStatusOrderByIssuedAtDesc(
                ticketIds, TicketSupportStatus.ACTIVE
        ).forEach(support -> mediumByTicketId.putIfAbsent(
                support.getTicket().getId(), support.getType()
        ));
        Set<Long> openTicketIds = new HashSet<>();
        journeyRepository.findAllByTicketIdInAndStatus(ticketIds, TicketJourneyStatus.OPEN)
                .forEach(journey -> openTicketIds.add(journey.getTicket().getId()));
        return new WalletRelations(mediumByTicketId, openTicketIds);
    }

    private int normalizedLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value < 1 || value > MAX_LIMIT) {
            throw badRequest("limit must be between 1 and " + MAX_LIMIT);
        }
        return value;
    }

    private <E extends Enum<E>> E enumValue(String value, Class<E> type, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("Unsupported " + field);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record WalletRelations(
            Map<Long, TicketSupportType> mediumByTicketId,
            Set<Long> openTicketIds
    ) {
    }
}
