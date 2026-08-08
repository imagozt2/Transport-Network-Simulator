package com.transport.simulator.ticketing.qr;

public final class TicketQrContract {

    public static final int WRAPPER_VERSION = 1;
    public static final int PAYLOAD_VERSION = 1;
    public static final String WRAPPER_PREFIX = "RMM:TICKET:" + WRAPPER_VERSION + ":";
    public static final String ISSUER = "rmm-ticketing";
    public static final String AUDIENCE = "rmm-validator";
    public static final String JWS_TYPE = "RMM-TICKET";
    public static final String JWS_ALGORITHM = "EdDSA";

    private TicketQrContract() {
    }
}
