package com.transport.simulator.dto.response.ticketrecharge;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.TicketSupportType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

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
        BigDecimal pricePerDay,
        List<Integer> tripOptions,
        List<Integer> dayOptions
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
                product.getPricePerDay(), tripOptions(ticket, product), dayOptions(product)
        );
    }

    private static List<Integer> tripOptions(Ticket ticket, TicketProduct product) {
        if (!product.isUsesTripBalance() || product.getMinTrips() == null
                || product.getMaxTrips() == null) {
            return List.of();
        }
        int currentTrips = ticket.getRemainingTrips() == null ? 0 : ticket.getRemainingTrips();
        int maximumRecharge = product.getMaxTrips() - currentTrips;
        if (maximumRecharge < product.getMinTrips()) {
            return List.of();
        }
        return IntStream.rangeClosed(product.getMinTrips(), maximumRecharge).boxed().toList();
    }

    private static List<Integer> dayOptions(TicketProduct product) {
        if (!product.isUsesDayValidity() || product.getMinDays() == null
                || product.getMaxDays() == null) {
            return List.of();
        }
        return IntStream.rangeClosed(product.getMinDays(), product.getMaxDays()).boxed().toList();
    }
}
