package com.transport.simulator.service.model;

import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.TrainStatus;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

public record RailwaySimulationState(
        ZonedDateTime evaluatedAt,
        ServiceOperationPhase phase,
        int activeLineCount,
        List<SimulatedLineState> lines,
        List<SimulatedTrainState> trains,
        List<PlannedDepotMovement> depotMovements
) {

    public RailwaySimulationState {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        lines = List.copyOf(lines);
        trains = List.copyOf(trains);
        depotMovements = List.copyOf(depotMovements);
        long actualActiveLines = lines.stream().filter(line -> line.operation().serviceOpen()).count();
        if (actualActiveLines != activeLineCount) {
            throw new IllegalArgumentException("activeLineCount does not match the simulated lines");
        }
        if ((phase == ServiceOperationPhase.CLOSED) != (activeLineCount == 0)) {
            throw new IllegalArgumentException("The network phase does not match its active lines");
        }
    }

    public long trainsInService() {
        return trains.stream().filter(train -> train.status() == TrainStatus.IN_SERVICE).count();
    }

    public long trainsInDepots() {
        return trains.stream().filter(train -> train.status() == TrainStatus.DEPOT).count();
    }
}
