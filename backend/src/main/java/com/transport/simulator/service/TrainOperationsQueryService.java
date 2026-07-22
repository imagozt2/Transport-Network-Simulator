package com.transport.simulator.service;

import com.transport.simulator.dto.response.trainoperation.TrainFleetSummaryResponse;
import com.transport.simulator.dto.response.trainoperation.TrainOperationDepotResponse;
import com.transport.simulator.dto.response.trainoperation.TrainOperationLineResponse;
import com.transport.simulator.dto.response.trainoperation.TrainOperationResponse;
import com.transport.simulator.dto.response.trainoperation.TrainOperationsResponse;
import com.transport.simulator.entity.Depot;
import com.transport.simulator.entity.Train;
import com.transport.simulator.entity.TrainModel;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.TrainStatus;
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

    public TrainOperationsQueryService(
            RailwaySimulationStateService railwaySimulationStateService,
            TrainRepository trainRepository
    ) {
        this.railwaySimulationStateService = railwaySimulationStateService;
        this.trainRepository = trainRepository;
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

        List<TrainOperationResponse> trains = trainRepository.findAllByActiveTrueOrderByCodeAsc()
                .stream()
                .map(train -> toTrainResponse(train, requiredSimulatedState(train, simulatedTrainsById)))
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

    private TrainOperationResponse toTrainResponse(Train train, SimulatedTrainState simulatedState) {
        validateStableAssignment(train, simulatedState);
        TrainModel model = train.getModel();

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
                toDepotResponse(train.getHomeDepot())
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
