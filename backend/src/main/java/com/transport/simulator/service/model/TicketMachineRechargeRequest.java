package com.transport.simulator.service.model;

import java.math.BigDecimal;

public record TicketMachineRechargeRequest(
        String rechargeReference,
        String qrValue,
        String originStationCode,
        String destinationStationCode,
        Integer trips,
        Integer days,
        BigDecimal balanceAmount,
        BigDecimal paidAmount
) {
    public TicketRechargeParameters parameters() {
        return new TicketRechargeParameters(
                originStationCode, destinationStationCode, trips, days, balanceAmount
        );
    }
}
