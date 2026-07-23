package com.transport.simulator.service.deviceevent;

public class InvalidDeviceEventPayloadException extends RuntimeException {

    public InvalidDeviceEventPayloadException(String eventId, Throwable cause) {
        super("Could not serialize payload for device event: " + eventId, cause);
    }
}
