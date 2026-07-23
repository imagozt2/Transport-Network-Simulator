package com.transport.simulator.service.deviceevent;

public class UnknownDeviceException extends RuntimeException {

    public UnknownDeviceException(String deviceCode) {
        super("Active device not found with code: " + deviceCode);
    }
}
