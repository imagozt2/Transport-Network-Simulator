package com.transport.simulator.dto.response.dashboard;

import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import java.util.Map;

public record DashboardDevicesResponse(
        long activeDevices,
        Map<DeviceStatus, Long> byStatus,
        Map<DeviceType, Long> byType
) {
}
