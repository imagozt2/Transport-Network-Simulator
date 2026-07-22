package com.transport.simulator.controller;

import com.transport.simulator.dto.response.trainoperation.TrainOperationsResponse;
import com.transport.simulator.service.TrainOperationsQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trains")
public class TrainOperationsController {

    private final TrainOperationsQueryService trainOperationsQueryService;

    public TrainOperationsController(TrainOperationsQueryService trainOperationsQueryService) {
        this.trainOperationsQueryService = trainOperationsQueryService;
    }

    @GetMapping("/operations")
    public TrainOperationsResponse getOperations() {
        return trainOperationsQueryService.getOperations();
    }
}
