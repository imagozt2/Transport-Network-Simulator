package com.transport.simulator.dto.response.transporttitle;

import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.TicketProductType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransportTitleResponse(
        Long id,
        String code,
        String name,
        String description,
        TicketProductType type,
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
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TransportTitleResponse from(TicketProduct product) {
        return new TransportTitleResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getDescription(),
                product.getProductType(),
                product.getBasePrice(),
                product.getPricePerStation(),
                product.getPricePerTrip(),
                product.getPricePerDay(),
                product.getMinTrips(),
                product.getMaxTrips(),
                product.getMinDays(),
                product.getMaxDays(),
                product.getMinRechargeAmount(),
                product.getMaxRechargeAmount(),
                product.isRequiresOriginDestination(),
                product.isUsesTripBalance(),
                product.isUsesDayValidity(),
                product.isUsesMoneyBalance(),
                product.isRechargeable(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
