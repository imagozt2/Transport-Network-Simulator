package com.transport.simulator.ticketing.qr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ticket-qr.signing")
public record TicketQrSigningProperties(
        String keyId,
        String privateKey,
        String publicKey,
        String retiredPublicKeys,
        long allowedClockSkewSeconds,
        int maximumQrLength
) {
}
