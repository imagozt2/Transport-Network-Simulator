package com.transport.simulator.dto.response.passengerticket;

import com.transport.simulator.entity.TicketProduct;
import java.math.BigDecimal;

public record PassengerTicketProductResponse(
        String code,
        String name,
        String description,
        String type,
        BigDecimal basePrice,
        BigDecimal pricePerStation,
        BigDecimal pricePerTrip,
        BigDecimal pricePerDay,
        Integer minTrips,
        Integer maxTrips,
        Integer minDays,
        Integer maxDays,
        BigDecimal minRechargeAmount,
        BigDecimal maxRechargeAmount,
        boolean requiresOriginDestination,
        boolean usesTripBalance,
        boolean usesDayValidity,
        boolean usesMoneyBalance,
        boolean rechargeable,
        String currency
) {
    public static PassengerTicketProductResponse from(TicketProduct product) {
        return new PassengerTicketProductResponse(
                product.getCode(), product.getName(), product.getDescription(),
                product.getProductType().name(), product.getBasePrice(),
                product.getPricePerStation(), product.getPricePerTrip(), product.getPricePerDay(),
                product.getMinTrips(), product.getMaxTrips(), product.getMinDays(), product.getMaxDays(),
                product.getMinRechargeAmount(), product.getMaxRechargeAmount(),
                product.isRequiresOriginDestination(), product.isUsesTripBalance(),
                product.isUsesDayValidity(), product.isUsesMoneyBalance(), product.isRechargeable(), "EUR"
        );
    }
}
