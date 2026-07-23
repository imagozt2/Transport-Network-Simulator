package com.transport.simulator.controller;

import com.transport.simulator.dto.response.deviceoperation.DeviceOperationsResponse;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.service.DeviceOperationsQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceOperationsController {

    private final DeviceOperationsQueryService queryService;

    public DeviceOperationsController(DeviceOperationsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/operations")
    public DeviceOperationsResponse getOperations(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DeviceType type,
            @RequestParam(required = false) DeviceStatus status,
            @RequestParam(required = false) String stationCode
    ) {
        return queryService.getOperations(search, type, status, stationCode);
    }
}
