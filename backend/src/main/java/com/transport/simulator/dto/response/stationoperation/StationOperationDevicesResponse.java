package com.transport.simulator.dto.response.stationoperation;

public record StationOperationDevicesResponse(
        long total,
        long ticketMachines,
        long entryValidators,
        long exitValidators,
        long online,
        long offline,
        long maintenance,
        long errors
) {
    public static StationOperationDevicesResponse empty() {
        return new StationOperationDevicesResponse(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
