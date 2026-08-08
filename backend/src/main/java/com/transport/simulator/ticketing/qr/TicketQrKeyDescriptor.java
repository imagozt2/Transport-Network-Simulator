package com.transport.simulator.ticketing.qr;

public record TicketQrKeyDescriptor(
        String keyId,
        TicketQrKeyStatus status
) {
}
