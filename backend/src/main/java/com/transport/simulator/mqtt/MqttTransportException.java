package com.transport.simulator.mqtt;

public class MqttTransportException extends RuntimeException {
    public MqttTransportException(String message) { super(message); }
    public MqttTransportException(String message, Throwable cause) { super(message, cause); }
}
