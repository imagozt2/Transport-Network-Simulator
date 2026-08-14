package com.transport.simulator.dto.request.ticketrecharge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketRechargeLookupRequest(
        @NotBlank @Size(max = 4096) String qrValue
) {
}
