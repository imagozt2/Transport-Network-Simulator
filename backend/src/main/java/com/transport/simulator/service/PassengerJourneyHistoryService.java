package com.transport.simulator.service;

import com.transport.simulator.dto.response.passengerjourney.PassengerJourneyHistoryItemResponse;
import com.transport.simulator.dto.response.passengerjourney.PassengerJourneyHistoryResponse;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.repository.TicketJourneyRepository;
import com.transport.simulator.security.PassengerPrincipal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PassengerJourneyHistoryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final PassengerResourceAccessService accessService;
    private final TicketJourneyRepository journeyRepository;

    public PassengerJourneyHistoryService(
            PassengerResourceAccessService accessService,
            TicketJourneyRepository journeyRepository
    ) {
        this.accessService = accessService;
        this.journeyRepository = journeyRepository;
    }

    @Transactional(readOnly = true)
    public PassengerJourneyHistoryResponse history(
            Integer limit,
            String cursor,
            Authentication authentication
    ) {
        PassengerPrincipal passenger = accessService.requirePassenger(authentication);
        int pageSize = limit == null ? DEFAULT_LIMIT : limit;
        if (pageSize < 1 || pageSize > MAX_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_LIMIT
            );
        }
        TicketJourney cursorJourney = cursor == null || cursor.isBlank()
                ? null
                : journeyRepository.findByCodeAndPassengerAccountId(cursor, passenger.accountId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Invalid journey history cursor"
                        ));
        List<TicketJourney> result = journeyRepository.findPassengerJourneyHistory(
                passenger.accountId(),
                cursorJourney == null ? null : cursorJourney.getOpenedAt(),
                cursorJourney == null ? null : cursorJourney.getId(),
                PageRequest.of(0, pageSize + 1)
        );
        boolean hasNext = result.size() > pageSize;
        List<TicketJourney> page = hasNext ? result.subList(0, pageSize) : result;
        return new PassengerJourneyHistoryResponse(
                page.stream().map(PassengerJourneyHistoryItemResponse::from).toList(),
                hasNext && !page.isEmpty() ? page.getLast().getCode() : null
        );
    }
}
