package com.transport.simulator.service.model;

import com.transport.simulator.enums.ServiceDirection;

public record TrainArrivalEstimate(
        ServiceDirection direction,
        int stationsAway,
        long secondsUntilArrival
) {
}
