package com.transport.simulator.dto.response.dashboard;

import java.util.List;

public record DashboardResponse(
        DashboardNetworkResponse network,
        DashboardFleetResponse fleet,
        DashboardDevicesResponse devices,
        DashboardDepotsResponse depots,
        List<DashboardLineResponse> lines
) {
}
