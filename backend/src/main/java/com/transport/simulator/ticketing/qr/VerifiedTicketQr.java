package com.transport.simulator.ticketing.qr;

import com.transport.simulator.entity.TicketQrCredential;

public record VerifiedTicketQr(
        TicketQrPayload payload,
        TicketQrCredential credential,
        String keyId,
        String fingerprint
) {
}
