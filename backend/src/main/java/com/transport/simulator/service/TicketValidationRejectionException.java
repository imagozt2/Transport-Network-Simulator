package com.transport.simulator.service;

public class TicketValidationRejectionException extends IllegalStateException {

    private final String reasonCode;

    public TicketValidationRejectionException(String reasonCode, String message) {
        super(message);
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode is required");
        }
        this.reasonCode = reasonCode.trim();
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
