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
        TrainOperationDepotResponse homeDepot,
        TrainOperationDepotResponse currentDepot,
        TrainServiceLocationResponse serviceLocation
) {
    public TrainOperationResponse {
        if (status == TrainStatus.IN_SERVICE && (serviceLocation == null || currentDepot != null)) {
            throw new IllegalArgumentException("An in-service train requires only a service location");
        }
        if (status == TrainStatus.DEPOT && (currentDepot == null || serviceLocation != null)) {
            throw new IllegalArgumentException("A depot train requires only a current depot");
        }
    }
}
