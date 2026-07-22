package com.transport.simulator.controller;

import com.transport.simulator.dto.response.stationoperation.StationOperationsResponse;
import com.transport.simulator.service.StationOperationsQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stations")
public class StationOperationsController {

    private final StationOperationsQueryService stationOperationsQueryService;

    public StationOperationsController(StationOperationsQueryService stationOperationsQueryService) {
        this.stationOperationsQueryService = stationOperationsQueryService;
    }

    @GetMapping("/operations")
    public StationOperationsResponse getOperations() {
        return stationOperationsQueryService.getOperations();
    }
}
