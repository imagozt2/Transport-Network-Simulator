package com.transport.simulator.dto.response.deviceoperation;

import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import java.time.LocalDateTime;

public record DeviceOperationResponse(
        Long id,
        String code,
        String name,
        DeviceType type,
        DeviceStatus status,
        LocalDateTime lastConnectionAt,
        DeviceOperationStationResponse station,
        DeviceOperationLastEventResponse lastEvent
) {
}
