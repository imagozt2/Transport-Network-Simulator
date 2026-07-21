package com.transport.simulator.controller;

import com.transport.simulator.dto.response.lineoperation.LineOperationsResponse;
import com.transport.simulator.service.LineOperationsQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lines")
public class LineOperationsController {

    private final LineOperationsQueryService lineOperationsQueryService;

    public LineOperationsController(LineOperationsQueryService lineOperationsQueryService) {
        this.lineOperationsQueryService = lineOperationsQueryService;
    }

    @GetMapping("/operations")
    public LineOperationsResponse getOperations() {
        return lineOperationsQueryService.getOperations();
    }
}
