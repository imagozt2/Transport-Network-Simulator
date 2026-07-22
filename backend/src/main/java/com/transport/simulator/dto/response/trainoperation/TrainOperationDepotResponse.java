package com.transport.simulator.dto.response.trainoperation;

public record TrainOperationDepotResponse(
        Long id,
        String code,
        String name,
        Long stationId,
        String stationCode,
        String stationName
) {
}
