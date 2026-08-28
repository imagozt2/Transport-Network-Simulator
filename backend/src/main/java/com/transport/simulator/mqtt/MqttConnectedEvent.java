package com.transport.simulator.mqtt;

import java.time.Instant;

public record MqttConnectedEvent(boolean reconnect, String serverUri, Instant connectedAt) {
}
