package com.transport.simulator.service.deviceevent;

public record DeviceEventReceipt(
        String eventId,
        Long logId,
        Status status
) {

    public enum Status {
        ACCEPTED,
        DUPLICATE
    }
}
