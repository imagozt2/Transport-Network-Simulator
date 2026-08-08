package com.transport.simulator.ticketing.qr;

import java.util.Objects;

public record SignedTicketQr(
        String value,
        String compactJws,
        String keyId,
        String fingerprint
) {
    public SignedTicketQr {
        Objects.requireNonNull(value);
        Objects.requireNonNull(compactJws);
        Objects.requireNonNull(keyId);
        Objects.requireNonNull(fingerprint);
    }
}
