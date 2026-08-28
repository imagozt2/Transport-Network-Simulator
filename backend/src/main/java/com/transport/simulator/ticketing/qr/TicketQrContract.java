package com.transport.simulator.ticketing.qr;

public final class TicketQrContract {

    public static final int LEGACY_WRAPPER_VERSION = 1;
    public static final int WRAPPER_VERSION = 2;
    public static final int PAYLOAD_VERSION = 1;
    public static final String LEGACY_WRAPPER_PREFIX =
            "RMM:TICKET:" + LEGACY_WRAPPER_VERSION + ":";
    public static final String WRAPPER_PREFIX = "RMM:TICKET:" + WRAPPER_VERSION + ":";
    public static final int OPAQUE_TOKEN_BYTES = 24;
    public static final int OPAQUE_TOKEN_LENGTH = 32;
    public static final String OPAQUE_CREDENTIAL_SCHEME = "opaque-v2";
    public static final String ISSUER = "rmm-ticketing";
    public static final String AUDIENCE = "rmm-validator";
    public static final String JWS_TYPE = "RMM-TICKET";
    public static final String JWS_ALGORITHM = "EdDSA";

    private TicketQrContract() {
    }
}
