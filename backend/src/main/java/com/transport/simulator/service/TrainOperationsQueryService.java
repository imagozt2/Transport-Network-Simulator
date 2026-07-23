package com.transport.simulator.service;

import com.transport.simulator.dto.response.trainoperation.TrainFleetSummaryResponse;
import com.transport.simulator.dto.response.trainoperation.TrainOperationDepotResponse;
import com.transport.simulator.dto.response.trainoperation.TrainOperationLineResponse;
import com.transport.simulator.dto.response.trainoperation.TrainOperationResponse;
import com.transport.simulator.dto.response.trainoperation.TrainOperationStationResponse;
import com.transport.simulator.dto.response.trainoperation.TrainOperationsResponse;
import com.transport.simulator.dto.response.trainoperation.TrainServiceLocationResponse;
import com.transport.simulator.entity.Depot;
import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.Train;
import com.transport.simulator.entity.TrainModel;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.service.model.RailwaySimulationState;
import com.transport.simulator.service.model.SimulatedTrainState;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TrainOperationsQueryService {

    private final RailwaySimulationStateService railwaySimulationStateService;
    private final TrainRepository trainRepository;
    private final LineStationRepository lineStationRepository;

    public TrainOperationsQueryService(
            RailwaySimulationStateService railwaySimulationStateService,
            TrainRepository trainRepository,
            LineStationRepository lineStationRepository
    ) {
        this.railwaySimulationStateService = railwaySimulationStateService;
        this.trainRepository = trainRepository;
        this.lineStationRepository = lineStationRepository;
    }

    public TrainOperationsResponse getOperations() {
        RailwaySimulationState simulation = railwaySimulationStateService.getCurrentState();
        Map<Long, SimulatedTrainState> simulatedTrainsById = simulation.trains().stream()
                .collect(Collectors.toUnmodifiableMap(
                        SimulatedTrainState::trainId,
                        Function.identity(),
                        (first, duplicate) -> {
                            throw new ServiceConfigurationException(
                                    "Train " + first.trainCode() + " has multiple simulated states"
                            );
                        }
                ));
        Map<Long, List<LineStation>> routesByLine = lineStationRepository
                .findAllByActiveTrueOrderByLineCodeAscStationOrderAsc()
                .stream()
                .collect(Collectors.groupingBy(lineStation -> lineStation.getLine().getId()));
        Map<Long, Map<Long, LineStation>> stationsByLineAndId = routesByLine.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().collect(Collectors.toUnmodifiableMap(
                                lineStation -> lineStation.getStation().getId(),
                                Function.identity()
                        ))
                ));

        List<TrainOperationResponse> trains = trainRepository.findAllByActiveTrueOrderByCodeAsc()
                .stream()
                .map(train -> toTrainResponse(
                        train,
                        requiredSimulatedState(train, simulatedTrainsById),
                        routesByLine,
                        stationsByLineAndId
                ))
                .toList();
        if (trains.size() != simulatedTrainsById.size()) {
            throw new ServiceConfigurationException(
                    "The simulated fleet does not match the active persisted fleet"
            );
        }

        return new TrainOperationsResponse(
                simulation.evaluatedAt(),
                simulation.phase(),
                summarizeFleet(trains),
                trains
        );
    }

    private TrainOperationResponse toTrainResponse(
            Train train,
            SimulatedTrainState simulatedState,
            Map<Long, List<LineStation>> routesByLine,
            Map<Long, Map<Long, LineStation>> stationsByLineAndId
    ) {
        validateStableAssignment(train, simulatedState);
        TrainModel model = train.getModel();
        TrainOperationDepotResponse homeDepot = toDepotResponse(train.getHomeDepot());

        return new TrainOperationResponse(
                train.getId(),
                train.getCode(),
                model.getManufacturer(),
                model.getModelName(),
                model.getSeries(),
                model.getCarCount(),
                model.getPassengerCapacity(),
                model.getMaximumSpeedKmh(),
                train.getFleetRole(),
                simulatedState.status(),
                train.getDispatchOrder(),
                toLineResponse(train.getAssignedLine()),
                homeDepot,
                currentDepot(train, simulatedState, homeDepot),
                serviceLocation(train, simulatedState, routesByLine, stationsByLineAndId)
        );
    }

    private TrainOperationDepotResponse currentDepot(
            Train train,
            SimulatedTrainState state,
            TrainOperationDepotResponse homeDepot
    ) {
        if (state.status() != TrainStatus.DEPOT) {
            return null;
        }
        if (!train.getHomeDepot().getId().equals(state.currentDepotId())
                || !train.getHomeDepot().getCode().equals(state.currentDepotCode())) {
            throw new ServiceConfigurationException(
                    "Simulated depot does not match the configured home depot for train " + train.getCode()
            );
        }
        return homeDepot;
    }

    private TrainServiceLocationResponse serviceLocation(
            Train train,
            SimulatedTrainState state,
            Map<Long, List<LineStation>> routesByLine,
            Map<Long, Map<Long, LineStation>> stationsByLineAndId
    ) {
        if (state.status() != TrainStatus.IN_SERVICE) {
            return null;
        }
        if (!train.getAssignedLine().getId().equals(state.currentLineId())) {
            throw new ServiceConfigurationException(
                    "Train " + train.getCode() + " is circulating on an unexpected line"
            );
        }
        List<LineStation> route = requiredRoute(train, routesByLine);
        Map<Long, LineStation> stationsById = stationsByLineAndId.get(train.getAssignedLine().getId());
        LineStation destination = state.direction() == ServiceDirection.OUTBOUND
                ? route.getLast()
                : route.getFirst();

        return new TrainServiceLocationResponse(
                toLineResponse(train.getAssignedLine()),
                state.dutyNumber(),
                state.positionState(),
                state.direction(),
                toStationResponse(destination),
                state.currentStationId() == null
                        ? null
                        : toStationResponse(requiredStation(stationsById, state.currentStationId(), train.getCode())),
                toStationResponse(requiredStation(stationsById, state.previousStationId(), train.getCode())),
                toStationResponse(requiredStation(stationsById, state.nextStationId(), train.getCode())),
                state.progressPercentage(),
                state.secondsUntilNextStation(),
                state.estimatedArrivalAt()
        );
    }

    private List<LineStation> requiredRoute(
            Train train,
            Map<Long, List<LineStation>> routesByLine
    ) {
        List<LineStation> route = routesByLine.get(train.getAssignedLine().getId());
        if (route == null || route.size() < 2) {
            throw new ServiceConfigurationException(
                    "Line " + train.getAssignedLine().getCode() + " requires at least two active stations"
            );
        }
        return route;
    }

    private LineStation requiredStation(
            Map<Long, LineStation> stationsById,
            Long stationId,
            String trainCode
    ) {
        LineStation station = stationsById == null ? null : stationsById.get(stationId);
        if (station == null) {
            throw new ServiceConfigurationException(
                    "Train " + trainCode + " references a station outside its route"
            );
        }
        return station;
    }

    private TrainOperationStationResponse toStationResponse(LineStation lineStation) {
        return new TrainOperationStationResponse(
                lineStation.getStation().getId(),
                lineStation.getStation().getCode(),
                lineStation.getStation().getName()
        );
    }

    private TrainOperationLineResponse toLineResponse(TransportLine line) {
        return new TrainOperationLineResponse(
                line.getId(),
                line.getCode(),
                line.getName(),
                line.getColor()
        );
    }

    private TrainOperationDepotResponse toDepotResponse(Depot depot) {
        return new TrainOperationDepotResponse(
                depot.getId(),
                depot.getCode(),
                depot.getName(),
                depot.getStation().getId(),
                depot.getStation().getCode(),
                depot.getStation().getName()
        );
    }

    private TrainFleetSummaryResponse summarizeFleet(List<TrainOperationResponse> trains) {
        Map<TrainStatus, Long> byStatus = initializeEnumCounts(TrainStatus.class);
        Map<FleetRole, Long> byRole = initializeEnumCounts(FleetRole.class);
        Map<String, Long> bySeries = new TreeMap<>();
        trains.forEach(train -> {
            byStatus.compute(train.status(), (status, total) -> total + 1);
            byRole.compute(train.fleetRole(), (role, total) -> total + 1);
            bySeries.merge(train.series(), 1L, Long::sum);
        });

        return new TrainFleetSummaryResponse(
                trains.size(),
                byStatus.get(TrainStatus.IN_SERVICE),
                byStatus.get(TrainStatus.DEPOT),
                byStatus,
                byRole,
                bySeries
        );
    }

    private <E extends Enum<E>> Map<E, Long> initializeEnumCounts(Class<E> enumType) {
        Map<E, Long> counts = new EnumMap<>(enumType);
        for (E value : enumType.getEnumConstants()) {
            counts.put(value, 0L);
        }
        return counts;
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

    private void validateStableAssignment(Train train, SimulatedTrainState state) {
        if (!train.getCode().equals(state.trainCode())
                || !train.getModel().getSeries().equals(state.trainSeries())
                || train.getFleetRole() != state.fleetRole()
                || !train.getAssignedLine().getId().equals(state.assignedLineId())) {
            throw new ServiceConfigurationException(
                    "Persisted and simulated data disagree for train " + train.getCode()
            );
        }
    }
}
