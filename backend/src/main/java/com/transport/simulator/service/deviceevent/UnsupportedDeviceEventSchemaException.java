package com.transport.simulator.service.deviceevent;

public class UnsupportedDeviceEventSchemaException extends RuntimeException {

    public UnsupportedDeviceEventSchemaException(String schemaVersion) {
        super("Unsupported device event schema version: " + schemaVersion);
    }
}
