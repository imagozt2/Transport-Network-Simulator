package com.transport.simulator.service.model;

import java.math.BigDecimal;

public record TicketRechargeParameters(
        String originStationCode,
        String destinationStationCode,
        Integer trips,
        Integer days,
        BigDecimal balanceAmount
) {

    public static TicketRechargeParameters singleTrip(String origin, String destination) {
        return new TicketRechargeParameters(origin, destination, null, null, null);
    }

    public static TicketRechargeParameters multiTrip(int trips) {
        return new TicketRechargeParameters(null, null, trips, null, null);
    }

    public static TicketRechargeParameters timePass(int days) {
        return new TicketRechargeParameters(null, null, null, days, null);
    }

    public static TicketRechargeParameters smartBalance(BigDecimal amount) {
        return new TicketRechargeParameters(null, null, null, null, amount);
    }
}
