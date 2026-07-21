package com.transport.simulator.service;

import com.transport.simulator.dto.response.lineoperation.LineOperationResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationStationResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationTrainResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationsResponse;
import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.LineStationRepository;
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

    public LineOperationsQueryService(
            RailwaySimulationStateService railwaySimulationStateService,
            TransportLineRepository transportLineRepository,
            LineStationRepository lineStationRepository
    ) {
        this.railwaySimulationStateService = railwaySimulationStateService;
        this.transportLineRepository = transportLineRepository;
        this.lineStationRepository = lineStationRepository;
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

        List<LineOperationResponse> lines = simulation.lines().stream()
                .map(line -> toLineResponse(
                        simulation,
                        line,
                        requiredLineDetails(line, lineDetails),
                        stationsByLine.getOrDefault(line.operation().lineId(), List.of()),
                        trainsByLine.getOrDefault(line.operation().lineId(), List.of())
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
            List<SimulatedTrainState> trains
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
                List.copyOf(stations),
                trainResponses
        );
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
}
