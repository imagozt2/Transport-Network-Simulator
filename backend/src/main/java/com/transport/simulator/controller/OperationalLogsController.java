package com.transport.simulator.controller;

import com.transport.simulator.dto.response.operationallog.OperationalLogsPageResponse;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.service.OperationalLogsQueryService;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
public class OperationalLogsController {

    private final OperationalLogsQueryService queryService;

    public OperationalLogsController(OperationalLogsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public OperationalLogsPageResponse getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) LogOrigin origin,
            @RequestParam(required = false) LogSeverity severity,
            @RequestParam(required = false) DeviceEventType eventType,
            @RequestParam(required = false) DeviceType deviceType,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) String stationCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime occurredFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime occurredTo
    ) {
        return queryService.getLogs(
                page,
                size,
                origin,
                severity,
                eventType,
                deviceType,
                deviceCode,
                stationCode,
                occurredFrom,
                occurredTo
        );
    }
}
