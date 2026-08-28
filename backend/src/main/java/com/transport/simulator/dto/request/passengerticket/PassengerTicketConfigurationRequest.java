package com.transport.simulator.dto.request.passengerticket;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PassengerTicketConfigurationRequest(
        @Size(max = 20) String originStationCode,
        @Size(max = 20) String destinationStationCode,
        @Positive Integer tripCount,
        @Positive Integer dayCount,
        @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal rechargeAmount
) {
}
