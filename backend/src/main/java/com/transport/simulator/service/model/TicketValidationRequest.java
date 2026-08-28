package com.transport.simulator.service.model;

import com.transport.simulator.enums.TicketQrValidationType;

public record TicketValidationRequest(
        String validationReference,
        TicketQrValidationType direction,
        String stationCode,
        String qrValue
) {
}
