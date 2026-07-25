package com.transport.simulator.service;

import com.transport.simulator.dto.response.lineoperation.LineOperationDepotResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationDepotStationResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationStationResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationTrainResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationsResponse;
import com.transport.simulator.entity.LineDepot;
import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.Train;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.LineDepotRepository;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.repository.TransportLineRepository;
import com.transport.simulator.service.model.LineDutyPlan;
import com.transport.simulator.service.model.RailwaySimulationState;
import com.transport.simulator.service.model.ServicePeriodFleetPlan;
import com.transport.simulator.service.model.SimulatedLineState;
import com.transport.simulator.service.model.SimulatedTrainState;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LineOperationsQueryService {

    private final RailwaySimulationStateService railwaySimulationStateService;
    private final TransportLineRepository transportLineRepository;
    private final LineStationRepository lineStationRepository;
    private final LineDepotRepository lineDepotRepository;
    private final TrainRepository trainRepository;

    public LineOperationsQueryService(
            RailwaySimulationStateService railwaySimulationStateService,
            TransportLineRepository transportLineRepository,
            LineStationRepository lineStationRepository,
            LineDepotRepository lineDepotRepository,
            TrainRepository trainRepository
    ) {
        this.railwaySimulationStateService = railwaySimulationStateService;
        this.transportLineRepository = transportLineRepository;
        this.lineStationRepository = lineStationRepository;
        this.lineDepotRepository = lineDepotRepository;
        this.trainRepository = trainRepository;
    }

    public LineOperationsResponse getOperations() {
        RailwaySimulationState simulation = railwaySimulationStateService.getCurrentState();
        Map<Long, TransportLine> lineDetails = transportLineRepository.findAllByActiveTrueOrderByCodeAsc()
                .stream()
                .collect(Collectors.toUnmodifiableMap(TransportLine::getId, Function.identity()));
        Map<Long, List<LineOperationStationResponse>> stationsByLine = lineStationRepository
                .findAllByActiveTrueOrderByLineCodeAscStationOrderAsc()
                .stream()
                .collect(Collectors.groupingBy(
                        lineStation -> lineStation.getLine().getId(),
                        Collectors.mapping(this::toStationResponse, Collectors.toList())
                ));
        Map<Long, List<SimulatedTrainState>> trainsByLine = simulation.trains().stream()
                .filter(train -> train.status() == TrainStatus.IN_SERVICE)
                .collect(Collectors.groupingBy(SimulatedTrainState::currentLineId));
        Map<Long, SimulatedTrainState> simulatedTrainsById = simulation.trains().stream()
                .collect(Collectors.toUnmodifiableMap(SimulatedTrainState::trainId, Function.identity()));
        Map<Long, List<LineDepot>> depotsByLine = lineDepotRepository
                .findAllByActiveTrueAndLineActiveTrueOrderByLineCodeAscDispatchPriorityAsc()
                .stream()
                .collect(Collectors.groupingBy(lineDepot -> lineDepot.getLine().getId()));
        Map<LineDepotFleetKey, List<Train>> fleetByLineAndDepot = trainRepository
                .findAllByActiveTrueOrderByCodeAsc()
                .stream()
                .collect(Collectors.groupingBy(train -> new LineDepotFleetKey(
                        train.getAssignedLine().getId(),
                        train.getHomeDepot().getId()
                )));

        List<LineOperationResponse> lines = simulation.lines().stream()
                .map(line -> toLineResponse(
                        simulation,
                        line,
                        requiredLineDetails(line, lineDetails),
                        stationsByLine.getOrDefault(line.operation().lineId(), List.of()),
                        trainsByLine.getOrDefault(line.operation().lineId(), List.of()),
                        depotsByLine.getOrDefault(line.operation().lineId(), List.of()),
                        fleetByLineAndDepot,
                        simulatedTrainsById
                ))
                .toList();

        return new LineOperationsResponse(
                simulation.evaluatedAt(),
                simulation.phase(),
                simulation.activeLineCount(),
                lines
        );
    }

    private LineOperationResponse toLineResponse(
            RailwaySimulationState simulation,
            SimulatedLineState lineState,
            TransportLine line,
            List<LineOperationStationResponse> stations,
            List<SimulatedTrainState> trains,
            List<LineDepot> lineDepots,
            Map<LineDepotFleetKey, List<Train>> fleetByLineAndDepot,
            Map<Long, SimulatedTrainState> simulatedTrainsById
    ) {
        if (stations.size() < 2) {
            throw new ServiceConfigurationException(
                    "Line " + line.getCode() + " requires at least two active stations"
            );
        }
        LineDutyPlan dutyPlan = lineState.dutyPlan().orElse(null);
        ServicePeriodFleetPlan currentPeriod = dutyPlan == null
                ? null
                : dutyPlan.periods().stream()
                        .filter(period -> !simulation.evaluatedAt().isBefore(period.startsAt()))
                        .filter(period -> simulation.evaluatedAt().isBefore(period.endsAt()))
                        .findFirst()
                        .orElseThrow(() -> new ServiceConfigurationException(
                                "No service period found at the evaluated time for line " + line.getCode()
                        ));
        List<LineOperationTrainResponse> trainResponses = trains.stream()
                .map(this::toTrainResponse)
                .toList();

        return new LineOperationResponse(
                line.getId(),
                line.getCode(),
                line.getName(),
                line.getColor(),
                lineState.operation().phase(),
                lineState.operation().serviceOpen(),
                dutyPlan == null ? null : dutyPlan.serviceStartsAt(),
                dutyPlan == null ? null : dutyPlan.serviceEndsAt(),
                currentPeriod == null ? null : currentPeriod.periodCode(),
                currentPeriod == null ? null : currentPeriod.periodType(),
                currentPeriod == null ? null : currentPeriod.headwaySeconds(),
                dutyPlan == null ? null : dutyPlan.roundTripSeconds() / 2,
                stations.size(),
                stations.getFirst(),
                stations.getLast(),
                trainResponses.size(),
                lineDepots.stream()
                        .map(lineDepot -> toDepotResponse(
                                lineDepot,
                                fleetByLineAndDepot.getOrDefault(
                                        new LineDepotFleetKey(line.getId(), lineDepot.getDepot().getId()),
                                        List.of()
                                ),
                                simulatedTrainsById
                        ))
                        .toList(),
                List.copyOf(stations),
                trainResponses
        );
    }

    private LineOperationDepotResponse toDepotResponse(
            LineDepot lineDepot,
            List<Train> assignedTrains,
            Map<Long, SimulatedTrainState> simulatedTrainsById
    ) {
        long trainsInService = assignedTrains.stream()
                .map(train -> requiredSimulatedState(train, simulatedTrainsById))
                .filter(state -> state.status() == TrainStatus.IN_SERVICE)
                .count();
        long availableTrains = simulatedTrainsById.values().stream()
                .filter(state -> lineDepot.getLine().getId().equals(state.assignedLineId()))
                .filter(state -> state.fleetRole() == FleetRole.REGULAR_SERVICE)
                .filter(state -> "9000".equals(state.trainSeries()))
                .filter(state -> state.status() == TrainStatus.DEPOT)
                .filter(state -> lineDepot.getDepot().getId().equals(state.currentDepotId()))
                .count();

        return new LineOperationDepotResponse(
                lineDepot.getDepot().getId(),
                lineDepot.getDepot().getCode(),
                lineDepot.getDepot().getName(),
                new LineOperationDepotStationResponse(
                        lineDepot.getDepot().getStation().getId(),
                        lineDepot.getDepot().getStation().getCode(),
                        lineDepot.getDepot().getStation().getName()
                ),
                new LineOperationDepotStationResponse(
                        lineDepot.getDispatchTerminalStation().getId(),
                        lineDepot.getDispatchTerminalStation().getCode(),
                        lineDepot.getDispatchTerminalStation().getName()
                ),
                lineDepot.getDispatchPriority(),
                lineDepot.isDispatchEnabled(),
                lineDepot.isReceptionEnabled(),
                assignedTrains.size(),
                trainsInService,
                availableTrains
        );
    }

    private SimulatedTrainState requiredSimulatedState(
            Train train,
            Map<Long, SimulatedTrainState> simulatedTrainsById
    ) {
        SimulatedTrainState state = simulatedTrainsById.get(train.getId());
        if (state == null) {
            throw new ServiceConfigurationException(
                    "Missing simulated state for active train " + train.getCode()
            );
        }
        return state;
    }

    private LineOperationTrainResponse toTrainResponse(SimulatedTrainState train) {
        return new LineOperationTrainResponse(
                train.trainId(),
                train.trainCode(),
                train.trainSeries(),
                train.dutyNumber(),
                train.positionState(),
                train.direction(),
                train.currentStationId(),
                train.currentStationCode(),
                train.previousStationId(),
                train.previousStationCode(),
                train.nextStationId(),
                train.nextStationCode(),
                train.progressPercentage(),
                train.secondsUntilNextStation(),
                train.estimatedArrivalAt()
        );
    }

    private LineOperationStationResponse toStationResponse(LineStation lineStation) {
        return new LineOperationStationResponse(
                lineStation.getStation().getId(),
                lineStation.getStation().getCode(),
                lineStation.getStation().getName(),
                lineStation.getStationOrder()
        );
    }

    private TransportLine requiredLineDetails(
            SimulatedLineState lineState,
            Map<Long, TransportLine> lineDetails
    ) {
        TransportLine line = lineDetails.get(lineState.operation().lineId());
        if (line == null) {
            throw new ServiceConfigurationException(
                    "Missing active line details for " + lineState.operation().lineCode()
            );
        }
        return line;
    }

    private record LineDepotFleetKey(Long lineId, Long depotId) {
    }
}
