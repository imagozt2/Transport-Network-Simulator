package com.transport.simulator.dto.response.deviceoperation;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceOperationsResponse(
        LocalDateTime evaluatedAt,
        DeviceOperationSummaryResponse summary,
        List<DeviceOperationResponse> devices
) {

    public DeviceOperationsResponse {
        devices = List.copyOf(devices);
    }
}
