package com.transport.simulator.dto.response.passengerticket;

import java.time.LocalDateTime;
import java.util.UUID;

public record PassengerTicketQrResponse(
        String ticketCode,
        String qrValue,
        UUID credentialId,
        LocalDateTime expiresAt
) {
}
