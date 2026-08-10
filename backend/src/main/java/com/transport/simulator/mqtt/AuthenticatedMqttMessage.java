package com.transport.simulator.mqtt;

public record AuthenticatedMqttMessage(
        AuthenticatedMqttMachine machine,
        String topic,
        byte[] payload
) {
    public AuthenticatedMqttMessage {
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
