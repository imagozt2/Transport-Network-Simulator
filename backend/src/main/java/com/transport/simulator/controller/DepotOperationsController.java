package com.transport.simulator.controller;

import com.transport.simulator.dto.response.depotoperation.DepotOperationsResponse;
import com.transport.simulator.service.DepotOperationsQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/depots")
public class DepotOperationsController {

    private final DepotOperationsQueryService depotOperationsQueryService;

    public DepotOperationsController(DepotOperationsQueryService depotOperationsQueryService) {
        this.depotOperationsQueryService = depotOperationsQueryService;
    }

    @GetMapping("/operations")
    public DepotOperationsResponse getOperations() {
        return depotOperationsQueryService.getOperations();
    }
}
