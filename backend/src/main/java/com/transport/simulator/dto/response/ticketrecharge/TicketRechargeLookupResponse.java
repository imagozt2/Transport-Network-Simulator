package com.transport.simulator.dto.response.ticketrecharge;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.TicketSupportType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketRechargeLookupResponse(
        String ticketCode,
        String productCode,
        String productName,
        String productType,
        String ticketStatus,
        TicketSupportType supportType,
        Integer remainingTrips,
        LocalDateTime validUntil,
        BigDecimal balanceAmount,
        String currency,
        boolean requiresOriginDestination,
        Integer minTrips,
        Integer maxTrips,
        Integer minDays,
        Integer maxDays,
        BigDecimal minRechargeAmount,
        BigDecimal maxRechargeAmount,
        BigDecimal basePrice,
        BigDecimal pricePerStation,
        BigDecimal pricePerTrip,
        BigDecimal pricePerDay
) {
    public static TicketRechargeLookupResponse from(Ticket ticket, TicketSupportType supportType) {
        TicketProduct product = ticket.getProduct();
        return new TicketRechargeLookupResponse(
                ticket.getCode(), product.getCode(), product.getName(),
                product.getProductType().name(), ticket.getStatus().name(), supportType,
                ticket.getRemainingTrips(), ticket.getValidUntil(), ticket.getBalanceAmount(),
                ticket.getCurrency(), product.isRequiresOriginDestination(), product.getMinTrips(),
                product.getMaxTrips(), product.getMinDays(), product.getMaxDays(),
                product.getMinRechargeAmount(), product.getMaxRechargeAmount(),
                product.getBasePrice(), product.getPricePerStation(), product.getPricePerTrip(),
                product.getPricePerDay()
        );
    }
}
