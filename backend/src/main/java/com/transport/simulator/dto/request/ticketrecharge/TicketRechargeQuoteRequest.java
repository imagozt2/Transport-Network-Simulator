package com.transport.simulator.dto.request.ticketrecharge;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record TicketRechargeQuoteRequest(
        @NotBlank @Size(max = 4096) String qrValue,
        @Size(max = 20) String originStationCode,
        @Size(max = 20) String destinationStationCode,
        @Min(1) @Max(100) Integer trips,
        @Min(1) @Max(365) Integer days,
        @DecimalMin("0.01") @DecimalMax("1000.00") BigDecimal balanceAmount
) {
}
