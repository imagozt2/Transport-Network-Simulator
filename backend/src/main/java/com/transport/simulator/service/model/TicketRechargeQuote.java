package com.transport.simulator.service.model;

import com.transport.simulator.enums.TicketProductType;
import java.math.BigDecimal;

public record TicketRechargeQuote(
        String ticketCode,
        TicketProductType productType,
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
}
