package com.transport.simulator.dto.request.transporttitle;

import com.transport.simulator.enums.CompensatoryDeliveryMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CompensatoryTicketIssuanceRequest(
        @Size(max = 50) String deviceCode,
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 20) String originStationCode,
        @Size(max = 20) String destinationStationCode,
        @Positive Integer trips,
        @Positive Integer days,
        @DecimalMin(value = "0.01") BigDecimal balanceAmount,
        CompensatoryDeliveryMethod deliveryMethod,
        @Size(max = 36) String passengerPublicId
) {

    public CompensatoryTicketIssuanceRequest(
            String deviceCode,
            String reason,
            String originStationCode,
            String destinationStationCode,
            Integer trips,
            Integer days,
            BigDecimal balanceAmount
    ) {
        this(deviceCode, reason, originStationCode, destinationStationCode,
                trips, days, balanceAmount, CompensatoryDeliveryMethod.PHYSICAL_DEVICE, null);
    }
}
