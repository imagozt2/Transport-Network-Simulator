package com.transport.simulator.controller;

import com.transport.simulator.dto.response.dashboard.DashboardResponse;
import com.transport.simulator.service.DashboardQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;

    public DashboardController(DashboardQueryService dashboardQueryService) {
        this.dashboardQueryService = dashboardQueryService;
    }

    @GetMapping("/summary")
    public DashboardResponse getSummary() {
        return dashboardQueryService.getSummary();
    }
}
