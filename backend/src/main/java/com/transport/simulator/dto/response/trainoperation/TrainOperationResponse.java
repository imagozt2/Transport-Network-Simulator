package com.transport.simulator.dto.response.trainoperation;

import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.TrainStatus;

public record TrainOperationResponse(
        Long id,
        String code,
        String manufacturer,
        String model,
        String series,
        int carCount,
        int passengerCapacity,
        int maximumSpeedKmh,
        FleetRole fleetRole,
        TrainStatus status,
        Integer dispatchOrder,
        TrainOperationLineResponse assignedLine,
        TrainOperationDepotResponse homeDepot
) {
}
