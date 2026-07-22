package com.transport.simulator.service;

import com.transport.simulator.dto.response.depotoperation.DepotFleetDistributionResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationsResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationsSummaryResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationStationResponse;
import com.transport.simulator.entity.Depot;
import com.transport.simulator.entity.Train;
import com.transport.simulator.enums.DepotOperationStatus;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.DepotRepository;
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
public class DepotOperationsQueryService {

    private static final int HIGH_OCCUPANCY_PERCENTAGE = 80;

    private final RailwaySimulationStateService railwaySimulationStateService;
    private final DepotRepository depotRepository;
    private final TrainRepository trainRepository;

    public DepotOperationsQueryService(
            RailwaySimulationStateService railwaySimulationStateService,
            DepotRepository depotRepository,
            TrainRepository trainRepository
    ) {
        this.railwaySimulationStateService = railwaySimulationStateService;
        this.depotRepository = depotRepository;
        this.trainRepository = trainRepository;
    }

    public DepotOperationsResponse getOperations() {
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
        List<Train> activeTrains = trainRepository.findAllByActiveTrueOrderByCodeAsc();
        validateFleet(activeTrains, simulatedTrainsById);

        Map<Long, List<Train>> assignedTrainsByDepot = activeTrains.stream()
                .collect(Collectors.groupingBy(train -> train.getHomeDepot().getId()));
        Map<Long, Long> occupancyByDepot = simulation.trains().stream()
                .filter(train -> train.status() == TrainStatus.DEPOT)
                .collect(Collectors.groupingBy(SimulatedTrainState::currentDepotId, Collectors.counting()));

        List<DepotOperationResponse> depots = depotRepository.findAllByActiveTrueOrderByCodeAsc().stream()
                .map(depot -> toDepotResponse(
                        depot,
                        assignedTrainsByDepot.getOrDefault(depot.getId(), List.of()),
                        occupancyByDepot.getOrDefault(depot.getId(), 0L),
                        simulatedTrainsById
                ))
                .toList();

        return new DepotOperationsResponse(
                simulation.evaluatedAt(),
                simulation.phase(),
                summarize(depots),
                depots
        );
    }

    private DepotOperationResponse toDepotResponse(
            Depot depot,
            List<Train> assignedTrains,
            long occupiedSpaces,
            Map<Long, SimulatedTrainState> simulatedTrainsById
    ) {
        int capacity = depot.getCapacity();
        long availableSpaces = Math.max(0, capacity - occupiedSpaces);
        int occupancyPercentage = percentage(occupiedSpaces, capacity);
        return new DepotOperationResponse(
                depot.getId(),
                depot.getCode(),
                depot.getName(),
                new DepotOperationStationResponse(
                        depot.getStation().getId(),
                        depot.getStation().getCode(),
                        depot.getStation().getName()
                ),
                capacity,
                depot.getTrackCount(),
                depot.getTrainsPerTrack(),
                occupiedSpaces,
                availableSpaces,
                occupancyPercentage,
                operationalStatus(occupiedSpaces, capacity, occupancyPercentage),
                fleetDistribution(assignedTrains, simulatedTrainsById)
        );
    }

    private DepotFleetDistributionResponse fleetDistribution(
            List<Train> assignedTrains,
            Map<Long, SimulatedTrainState> simulatedTrainsById
    ) {
        Map<TrainStatus, Long> byStatus = initializeEnumCounts(TrainStatus.class);
        Map<FleetRole, Long> byRole = initializeEnumCounts(FleetRole.class);
        Map<String, Long> bySeries = new TreeMap<>();
        assignedTrains.forEach(train -> {
            SimulatedTrainState state = requiredState(train, simulatedTrainsById);
            byStatus.compute(state.status(), (status, total) -> total + 1);
            byRole.compute(train.getFleetRole(), (role, total) -> total + 1);
            bySeries.merge(train.getModel().getSeries(), 1L, Long::sum);
        });
        return new DepotFleetDistributionResponse(
                assignedTrains.size(),
                byStatus.get(TrainStatus.IN_SERVICE),
                byStatus,
                byRole,
                bySeries
        );
    }

    private DepotOperationsSummaryResponse summarize(List<DepotOperationResponse> depots) {
        long totalCapacity = depots.stream().mapToLong(DepotOperationResponse::capacity).sum();
        long occupiedSpaces = depots.stream().mapToLong(DepotOperationResponse::occupiedSpaces).sum();
        long assignedFleet = depots.stream().mapToLong(depot -> depot.fleet().assignedTrainCount()).sum();
        long trainsInService = depots.stream().mapToLong(depot -> depot.fleet().assignedTrainsInService()).sum();
        return new DepotOperationsSummaryResponse(
                depots.size(),
                totalCapacity,
                occupiedSpaces,
                Math.max(0, totalCapacity - occupiedSpaces),
                percentage(occupiedSpaces, totalCapacity),
                assignedFleet,
                trainsInService
        );
    }

    private DepotOperationStatus operationalStatus(long occupiedSpaces, int capacity, int percentage) {
        if (occupiedSpaces > capacity) {
            return DepotOperationStatus.OVER_CAPACITY;
        }
        if (occupiedSpaces == capacity) {
            return DepotOperationStatus.FULL;
        }
        if (occupiedSpaces == 0) {
            return DepotOperationStatus.EMPTY;
        }
        if (percentage >= HIGH_OCCUPANCY_PERCENTAGE) {
            return DepotOperationStatus.HIGH_OCCUPANCY;
        }
        return DepotOperationStatus.AVAILABLE;
    }

    private int percentage(long value, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.toIntExact(Math.round(value * 100.0 / total));
    }

    private void validateFleet(List<Train> activeTrains, Map<Long, SimulatedTrainState> states) {
        if (activeTrains.size() != states.size()) {
            throw new ServiceConfigurationException("The simulated fleet does not match the active persisted fleet");
        }
        activeTrains.forEach(train -> {
            SimulatedTrainState state = requiredState(train, states);
            if (!train.getCode().equals(state.trainCode())
                    || !train.getModel().getSeries().equals(state.trainSeries())
                    || train.getFleetRole() != state.fleetRole()
                    || !train.getAssignedLine().getId().equals(state.assignedLineId())) {
                throw new ServiceConfigurationException(
                        "Persisted and simulated data disagree for train " + train.getCode()
                );
            }
        });
    }

    private SimulatedTrainState requiredState(Train train, Map<Long, SimulatedTrainState> states) {
        SimulatedTrainState state = states.get(train.getId());
        if (state == null) {
            throw new ServiceConfigurationException("Missing simulated state for active train " + train.getCode());
        }
        return state;
    }

    private <E extends Enum<E>> Map<E, Long> initializeEnumCounts(Class<E> enumType) {
        Map<E, Long> counts = new EnumMap<>(enumType);
        for (E value : enumType.getEnumConstants()) {
            counts.put(value, 0L);
        }
        return counts;
    }
}
