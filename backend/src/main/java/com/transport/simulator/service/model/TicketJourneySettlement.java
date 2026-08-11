package com.transport.simulator.service.model;

import java.math.BigDecimal;
import java.util.Objects;

public record TicketJourneySettlement(int stationCount, BigDecimal fareAmount) {

    public TicketJourneySettlement {
        if (stationCount <= 0) {
            throw new IllegalArgumentException("stationCount must be positive");
        }
        Objects.requireNonNull(fareAmount, "fareAmount is required");
        if (fareAmount.signum() < 0) {
            throw new IllegalArgumentException("fareAmount cannot be negative");
        }
    }
}
