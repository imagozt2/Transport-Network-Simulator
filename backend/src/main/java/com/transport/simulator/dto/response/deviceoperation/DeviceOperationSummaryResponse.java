package com.transport.simulator.dto.response.deviceoperation;

import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import java.util.Map;

public record DeviceOperationSummaryResponse(
        long totalDevices,
        long filteredDevices,
        Map<DeviceType, Long> byType,
        Map<DeviceStatus, Long> byStatus
) {

    public DeviceOperationSummaryResponse {
        byType = Map.copyOf(byType);
        byStatus = Map.copyOf(byStatus);
    }
}
