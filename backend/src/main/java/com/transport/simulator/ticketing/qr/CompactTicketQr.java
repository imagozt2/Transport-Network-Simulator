package com.transport.simulator.ticketing.qr;

import java.util.Objects;

public record CompactTicketQr(String value, String fingerprint) {

    public CompactTicketQr {
        Objects.requireNonNull(value, "value is required");
        Objects.requireNonNull(fingerprint, "fingerprint is required");
    }
}
