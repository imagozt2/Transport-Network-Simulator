package com.transport.simulator.service.model;

import com.transport.simulator.entity.Station;
import java.math.BigDecimal;

public record TicketIssuanceParameters(
        Station originStation,
        Station destinationStation,
        Integer stationCount,
        Integer trips,
        Integer days,
        BigDecimal balanceAmount
) {

    public static TicketIssuanceParameters singleTrip(
            Station origin,
            Station destination,
            int stationCount
    ) {
        return new TicketIssuanceParameters(origin, destination, stationCount, null, null, null);
    }

    public static TicketIssuanceParameters multiTrip(int trips) {
        return new TicketIssuanceParameters(null, null, null, trips, null, null);
    }

    public static TicketIssuanceParameters timePass(int days) {
        return new TicketIssuanceParameters(null, null, null, null, days, null);
    }

    public static TicketIssuanceParameters smartBalance(BigDecimal balance) {
        return new TicketIssuanceParameters(null, null, null, null, null, balance);
    }
}
