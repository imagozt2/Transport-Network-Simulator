package com.transport.simulator.service.model;

import java.math.BigDecimal;

public record TicketMachinePurchaseRequest(
        String purchaseReference,
        String productCode,
        String originStationCode,
        String destinationStationCode,
        Integer quantity,
        BigDecimal rechargeAmount,
        BigDecimal paidAmount
) {
}
