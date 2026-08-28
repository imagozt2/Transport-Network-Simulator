package com.transport.simulator.controller;

import com.transport.simulator.dto.response.passengerjourney.PassengerJourneyHistoryResponse;
import com.transport.simulator.service.PassengerJourneyHistoryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rmm-app/v1/journeys/history")
public class PassengerJourneyHistoryController {

    private final PassengerJourneyHistoryService historyService;

    public PassengerJourneyHistoryController(PassengerJourneyHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public PassengerJourneyHistoryResponse history(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            Authentication authentication
    ) {
        return historyService.history(limit, cursor, authentication);
    }
}
