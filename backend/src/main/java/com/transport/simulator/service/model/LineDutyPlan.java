package com.transport.simulator.service.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

public record LineDutyPlan(
        Long lineId,
        String lineCode,
        LocalDate serviceDate,
        ZonedDateTime serviceStartsAt,
        ZonedDateTime serviceEndsAt,
        long roundTripSeconds,
        List<ServicePeriodFleetPlan> periods,
        List<PlannedTrainDuty> duties,
        List<SimulatedTrainPosition> positions,
        List<PlannedDepotMovement> depotMovements
) {

    public LineDutyPlan {
        Objects.requireNonNull(lineId, "lineId must not be null");
        Objects.requireNonNull(lineCode, "lineCode must not be null");
        Objects.requireNonNull(serviceDate, "serviceDate must not be null");
        Objects.requireNonNull(serviceStartsAt, "serviceStartsAt must not be null");
        Objects.requireNonNull(serviceEndsAt, "serviceEndsAt must not be null");
        if (!serviceEndsAt.isAfter(serviceStartsAt) || roundTripSeconds <= 0) {
            throw new IllegalArgumentException("The service window and round trip must be positive");
        }
        periods = List.copyOf(periods);
        duties = List.copyOf(duties);
        positions = List.copyOf(positions);
        depotMovements = List.copyOf(depotMovements);
    }

    public long activeDutyCountAt(ZonedDateTime instant) {
        return duties.stream().filter(duty -> duty.isActiveAt(instant)).count();
    }
}
