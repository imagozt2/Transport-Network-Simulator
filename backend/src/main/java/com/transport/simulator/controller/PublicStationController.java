package com.transport.simulator.controller;

import com.transport.simulator.dto.response.passengernetwork.PassengerNetworkStationsResponse;
import com.transport.simulator.service.PassengerNetworkQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/v1/stations")
public class PublicStationController {

    private final PassengerNetworkQueryService networkQueryService;

    public PublicStationController(PassengerNetworkQueryService networkQueryService) {
        this.networkQueryService = networkQueryService;
    }

    @GetMapping
    public PassengerNetworkStationsResponse stations() {
        return networkQueryService.stations(null, null);
    }
}
