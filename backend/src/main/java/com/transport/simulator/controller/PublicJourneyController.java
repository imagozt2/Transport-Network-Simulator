package com.transport.simulator.controller;

import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkJourneyResponse;
import com.transport.simulator.service.PassengerNetworkQueryService;
import com.transport.simulator.service.ServiceConfigurationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/v1/journeys")
public class PublicJourneyController {

    private final PassengerNetworkQueryService networkQueryService;

    public PublicJourneyController(PassengerNetworkQueryService networkQueryService) {
        this.networkQueryService = networkQueryService;
    }

    @GetMapping
    public PassengerNetworkJourneyResponse journey(
            @RequestParam String origin,
            @RequestParam String destination
    ) {
        try {
            return networkQueryService.journey(origin, destination);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (ServiceConfigurationException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        }
    }
}
