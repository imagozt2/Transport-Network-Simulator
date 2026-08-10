package com.transport.simulator.mqtt;

import java.time.Instant;

public record MqttConnectionSnapshot(
        MqttConnectionState state,
        String clientId,
        String serverUri,
        Instant changedAt,
        String lastError
) {
}
