package com.transport.simulator.ticketing.qr;

public class TicketQrReferenceReuseException extends RuntimeException {

    public TicketQrReferenceReuseException() {
        super("The validation reference was already used for a different QR operation");
    }
}
