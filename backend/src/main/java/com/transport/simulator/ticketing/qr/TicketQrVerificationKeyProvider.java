package com.transport.simulator.ticketing.qr;

import java.security.PublicKey;
import org.springframework.stereotype.Component;

@Component
public class TicketQrVerificationKeyProvider {

    private final TicketQrKeyRing keyRing;

    public TicketQrVerificationKeyProvider(TicketQrKeyRing keyRing) {
        this.keyRing = keyRing;
    }

    public PublicKey findTrustedKey(String keyId) {
        return keyRing.trustedPublicKey(keyId);
    }
}
