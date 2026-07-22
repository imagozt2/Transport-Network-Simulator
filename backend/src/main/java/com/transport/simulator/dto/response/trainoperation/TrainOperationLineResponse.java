package com.transport.simulator.dto.response.trainoperation;

public record TrainOperationLineResponse(
        Long id,
        String code,
        String name,
        String color
) {
}
