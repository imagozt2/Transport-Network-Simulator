package com.transport.simulator.ticketing.qr;

import java.security.PrivateKey;
import org.springframework.stereotype.Component;

@Component
public class TicketQrSigningKeyProvider {

    private final TicketQrKeyRing keyRing;

    public TicketQrSigningKeyProvider(TicketQrKeyRing keyRing) {
        this.keyRing = keyRing;
    }

    public String activeKeyId() {
        return keyRing.activeKeyId();
    }

    public PrivateKey activePrivateKey() {
        return keyRing.activePrivateKey();
    }
}
