package com.transport.simulator.service;

import com.transport.simulator.entity.Train;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.service.model.LineDutyPlan;
import com.transport.simulator.service.model.LineServiceOperationState;
import com.transport.simulator.service.model.PlannedDepotMovement;
import com.transport.simulator.service.model.RailwaySimulationState;
import com.transport.simulator.service.model.ServiceDutyPlan;
import com.transport.simulator.service.model.ServiceOperationState;
import com.transport.simulator.service.model.SimulatedLineState;
import com.transport.simulator.service.model.SimulatedTrainPosition;
import com.transport.simulator.service.model.SimulatedTrainState;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RailwaySimulationStateService {

    private final Clock serviceClock;
    private final ServiceOperationStateService serviceOperationStateService;
    private final TrainDutyPlanningService trainDutyPlanningService;
    private final TrainRepository trainRepository;

    public RailwaySimulationStateService(
            Clock serviceClock,
            ServiceOperationStateService serviceOperationStateService,
            TrainDutyPlanningService trainDutyPlanningService,
            TrainRepository trainRepository
    ) {
        this.serviceClock = serviceClock;
        this.serviceOperationStateService = serviceOperationStateService;
        this.trainDutyPlanningService = trainDutyPlanningService;
        this.trainRepository = trainRepository;
    }

    public RailwaySimulationState getCurrentState() {
        return getStateAt(ZonedDateTime.now(serviceClock));
    }

    public RailwaySimulationState getStateAt(ZonedDateTime requestedDateTime) {
        ZonedDateTime evaluatedAt = requestedDateTime.withZoneSameInstant(serviceClock.getZone());
        ServiceOperationState operationState = serviceOperationStateService.getStateAt(evaluatedAt);
        ServiceDutyPlan dutyPlan = trainDutyPlanningService.getPlan(operationState);
        Map<Long, LineDutyPlan> plansByLine = dutyPlan.lines().stream()
                .collect(Collectors.toUnmodifiableMap(LineDutyPlan::lineId, Function.identity()));
        List<SimulatedLineState> lines = operationState.lines().stream()
                .map(line -> combineLineState(line, plansByLine))
                .toList();
        Map<Long, SimulatedTrainPosition> positionsByTrain = dutyPlan.lines().stream()
                .flatMap(line -> line.positions().stream())
                .collect(Collectors.toUnmodifiableMap(
                        SimulatedTrainPosition::trainId,
                        Function.identity(),
                        (first, duplicate) -> {
                            throw new ServiceConfigurationException(
                                    "Train " + first.trainCode() + " has multiple simultaneous positions"
                            );
                        }
                ));
        List<SimulatedTrainState> trains = trainRepository.findAllByActiveTrueOrderByCodeAsc().stream()
                .map(train -> toTrainState(train, positionsByTrain.get(train.getId())))
                .toList();
        List<PlannedDepotMovement> depotMovements = dutyPlan.lines().stream()
                .flatMap(line -> line.depotMovements().stream())
                .sorted(Comparator.comparing(PlannedDepotMovement::scheduledAt)
                        .thenComparing(PlannedDepotMovement::lineCode)
                        .thenComparingInt(PlannedDepotMovement::dutyNumber))
                .toList();

        return new RailwaySimulationState(
                evaluatedAt,
                operationState.phase(),
                operationState.activeLineCount(),
                lines,
                trains,
                depotMovements
        );
    }

    private SimulatedLineState combineLineState(
            LineServiceOperationState operation,
            Map<Long, LineDutyPlan> plansByLine
    ) {
        return new SimulatedLineState(operation, Optional.ofNullable(plansByLine.get(operation.lineId())));
    }

    private SimulatedTrainState toTrainState(Train train, SimulatedTrainPosition position) {
        if (position == null) {
            return trainInDepot(train);
        }
        return new SimulatedTrainState(
                train.getId(),
                train.getCode(),
                train.getModel().getSeries(),
                train.getFleetRole(),
                TrainStatus.IN_SERVICE,
                train.getAssignedLine().getId(),
                train.getAssignedLine().getCode(),
                position.lineId(),
                position.lineCode(),
                null,
                null,
                position.dutyNumber(),
                position.state(),
                position.direction(),
                position.currentStationId(),
                position.currentStationCode(),
                position.previousStationId(),
                position.previousStationCode(),
                position.nextStationId(),
                position.nextStationCode(),
                position.progressPercentage(),
                position.secondsUntilNextStation(),
                position.estimatedArrivalAt()
        );
    }

    private SimulatedTrainState trainInDepot(Train train) {
        return new SimulatedTrainState(
                train.getId(),
                train.getCode(),
                train.getModel().getSeries(),
                train.getFleetRole(),
                TrainStatus.DEPOT,
                train.getAssignedLine().getId(),
                train.getAssignedLine().getCode(),
                null,
                null,
                train.getHomeDepot().getId(),
                train.getHomeDepot().getCode(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
