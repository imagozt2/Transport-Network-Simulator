package com.transport.simulator.controller;

import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkLinesResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkJourneyResponse;
import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkStationsResponse;
import com.transport.simulator.service.PassengerNetworkQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.transport.simulator.service.ServiceConfigurationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rmm-app/v1/network")
public class PassengerNetworkController {

    private final PassengerNetworkQueryService networkQueryService;

    public PassengerNetworkController(PassengerNetworkQueryService networkQueryService) {
        this.networkQueryService = networkQueryService;
    }

    @GetMapping("/lines")
    public PassengerNetworkLinesResponse lines() {
        return networkQueryService.lines();
    }

    @GetMapping("/stations")
    public PassengerNetworkStationsResponse stations(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String lineCode
    ) {
        return networkQueryService.stations(query, lineCode);
    }

    @GetMapping("/journeys")
    public PassengerNetworkJourneyResponse journey(
            @RequestParam String origin,
            @RequestParam String destination
    ) {
        try {
            return networkQueryService.journey(origin, destination);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (ServiceConfigurationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    exception.getMessage()
            );
        }
    }
}
