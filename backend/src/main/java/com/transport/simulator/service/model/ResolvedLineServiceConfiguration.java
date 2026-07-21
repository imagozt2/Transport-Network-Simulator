package com.transport.simulator.service.model;

import com.transport.simulator.enums.OperatingDayType;
import com.transport.simulator.enums.ServicePeriodType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ResolvedLineServiceConfiguration(
        Long lineId,
        String lineCode,
        LocalDate serviceDate,
        String calendarCode,
        OperatingDayType dayType,
        LocalTime serviceStartTime,
        LocalTime serviceEndTime,
        String periodCode,
        ServicePeriodType periodType,
        LocalTime periodStartTime,
        LocalTime periodEndTime,
        int headwaySeconds,
        List<RouteStopConfiguration> route,
        List<LineDepotConfiguration> depots
) {

    public ResolvedLineServiceConfiguration {
        route = List.copyOf(route);
        depots = List.copyOf(depots);
    }
}
