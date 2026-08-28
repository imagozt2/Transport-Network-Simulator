package com.transport.simulator.mqtt;

import com.transport.simulator.enums.DeviceType;

public record AuthenticatedMqttMachine(
        Long deviceId,
        String deviceCode,
        DeviceType deviceType,
        String stationCode,
        String instanceId,
        String mqttClientId
) {
}
