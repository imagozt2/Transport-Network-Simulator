package com.transport.simulator.dto.response.ticketrecharge;

import com.transport.simulator.service.model.TicketRechargeQuote;
import java.math.BigDecimal;

public record TicketRechargeQuoteResponse(
        String ticketCode,
        String productType,
        String originStationCode,
        String destinationStationCode,
        Integer stationCount,
        Integer trips,
        Integer resultingTrips,
        Integer days,
        BigDecimal balanceAmount,
        BigDecimal resultingBalanceAmount,
        BigDecimal totalAmount,
        String currency
) {
    public static TicketRechargeQuoteResponse from(TicketRechargeQuote quote) {
        return new TicketRechargeQuoteResponse(
                quote.ticketCode(), quote.productType().name(), quote.originStationCode(),
                quote.destinationStationCode(), quote.stationCount(), quote.trips(),
                quote.resultingTrips(), quote.days(), quote.balanceAmount(),
                quote.resultingBalanceAmount(), quote.totalAmount(), quote.currency()
        );
    }
}
