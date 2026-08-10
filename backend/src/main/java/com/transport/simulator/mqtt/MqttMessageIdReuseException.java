package com.transport.simulator.mqtt;

public class MqttMessageIdReuseException extends RuntimeException {
    public MqttMessageIdReuseException() {
        super("MQTT messageId was reused with a different machine, topic or payload");
    }
}
