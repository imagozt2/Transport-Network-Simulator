package com.transport.simulator.dto.request.passengerticket;

import com.transport.simulator.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PassengerTicketPurchaseRequest(
        @NotBlank @Size(max = 50) String productCode,
        @NotNull @Valid PassengerTicketConfigurationRequest configuration,
        @NotNull PaymentMethod paymentMethod
) {
}
