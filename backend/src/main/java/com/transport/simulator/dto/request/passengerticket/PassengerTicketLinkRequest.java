package com.transport.simulator.dto.request.passengerticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PassengerTicketLinkRequest(
        @NotBlank @Size(max = 4096) String qrValue,
        @NotBlank @Size(min = 4, max = 40) String linkCode
) {
}
