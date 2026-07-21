package com.transport.simulator.enums;

public enum ServiceDirection {
    OUTBOUND(1),
    INBOUND(-1);

    private final short value;

    ServiceDirection(int value) {
        this.value = (short) value;
    }

    public short getValue() {
        return value;
    }
}
