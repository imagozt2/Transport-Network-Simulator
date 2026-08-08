package com.transport.simulator.ticketing.qr;

public class TicketQrVerificationException extends RuntimeException {

    private final TicketQrVerificationFailure failure;

    public TicketQrVerificationException(TicketQrVerificationFailure failure) {
        super(failure.name());
        this.failure = failure;
    }

    public TicketQrVerificationException(TicketQrVerificationFailure failure, Throwable cause) {
        super(failure.name(), cause);
        this.failure = failure;
    }

    public TicketQrVerificationFailure getFailure() {
        return failure;
    }
}
