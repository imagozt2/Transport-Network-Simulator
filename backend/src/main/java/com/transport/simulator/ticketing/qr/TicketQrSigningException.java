package com.transport.simulator.ticketing.qr;

public class TicketQrSigningException extends RuntimeException {

    public TicketQrSigningException(String message) {
        super(message);
    }

    public TicketQrSigningException(String message, Throwable cause) {
        super(message, cause);
    }
}
