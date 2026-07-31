package com.transport.simulator.dto.request.transporttitle;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CompensatoryTicketIssuanceRequest(
        @NotBlank @Size(max = 50) String deviceCode,
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 20) String originStationCode,
        @Size(max = 20) String destinationStationCode,
        @Positive Integer trips,
        @Positive Integer days,
        @DecimalMin(value = "0.01") BigDecimal balanceAmount
) {
}
