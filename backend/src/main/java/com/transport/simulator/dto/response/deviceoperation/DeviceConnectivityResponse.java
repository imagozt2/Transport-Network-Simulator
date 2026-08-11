package com.transport.simulator.dto.response.deviceoperation;

import com.transport.simulator.enums.DeviceConnectivityState;
import com.transport.simulator.enums.DeviceMqttPresence;
import com.transport.simulator.enums.DeviceOperationalState;
import java.time.LocalDateTime;

public record DeviceConnectivityResponse(
        DeviceConnectivityState state,
        DeviceMqttPresence mqttPresence,
        DeviceOperationalState operationalState,
        LocalDateTime lastCommunicationAt,
        LocalDateTime lastPresenceAt,
        LocalDateTime lastStatusAt,
        String serviceMode,
        String softwareVersion,
        Long uptimeSeconds
) {
}
