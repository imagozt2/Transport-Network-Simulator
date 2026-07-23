package com.transport.simulator.service;

import com.transport.simulator.dto.response.depotoperation.DepotFleetDistributionResponse;
import com.transport.simulator.dto.response.depotoperation.DepotMovementLineResponse;
import com.transport.simulator.dto.response.depotoperation.DepotMovementResponse;
import com.transport.simulator.dto.response.depotoperation.DepotMovementsSummaryResponse;
import com.transport.simulator.dto.response.depotoperation.DepotMovementTrainResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationsResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationsSummaryResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationStationResponse;
import com.transport.simulator.entity.Depot;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Train;
import com.transport.simulator.enums.DepotMovementStatus;
import com.transport.simulator.enums.DepotMovementType;
import com.transport.simulator.enums.DepotOperationStatus;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.DepotRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.service.model.PlannedDepotMovement;
import com.transport.simulator.service.model.RailwaySimulationState;
import com.transport.simulator.service.model.SimulatedTrainState;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final StationRepository stationRepository;

    public DepotOperationsQueryService(
            RailwaySimulationStateService railwaySimulationStateService,
            DepotRepository depotRepository,
            TrainRepository trainRepository,
            StationRepository stationRepository
    ) {
        this.railwaySimulationStateService = railwaySimulationStateService;
        this.depotRepository = depotRepository;
        this.trainRepository = trainRepository;
        this.stationRepository = stationRepository;
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
        Map<Long, Train> trainsById = activeTrains.stream()
                .collect(Collectors.toUnmodifiableMap(Train::getId, Function.identity()));
        Set<Long> movementStationIds = simulation.depotMovements().stream()
                .map(PlannedDepotMovement::stationId)
                .collect(Collectors.toUnmodifiableSet());
        Map<Long, Station> stationsById = stationRepository.findAllById(movementStationIds).stream()
                .collect(Collectors.toUnmodifiableMap(Station::getId, Function.identity()));

        Map<Long, List<Train>> assignedTrainsByDepot = activeTrains.stream()
                .collect(Collectors.groupingBy(train -> train.getHomeDepot().getId()));
        Map<Long, Long> occupancyByDepot = simulation.trains().stream()
                .filter(train -> train.status() == TrainStatus.DEPOT)
                .collect(Collectors.groupingBy(SimulatedTrainState::currentDepotId, Collectors.counting()));
        Map<Long, List<PlannedDepotMovement>> movementsByDepot = simulation.depotMovements().stream()
                .collect(Collectors.groupingBy(PlannedDepotMovement::depotId));

        List<DepotOperationResponse> depots = depotRepository.findAllByActiveTrueOrderByCodeAsc().stream()
                .map(depot -> toDepotResponse(
                        depot,
                        assignedTrainsByDepot.getOrDefault(depot.getId(), List.of()),
                        occupancyByDepot.getOrDefault(depot.getId(), 0L),
                        simulatedTrainsById,
                        movementsByDepot.getOrDefault(depot.getId(), List.of()),
                        simulation.evaluatedAt(),
                        trainsById,
                        stationsById
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
            Map<Long, SimulatedTrainState> simulatedTrainsById,
            List<PlannedDepotMovement> plannedMovements,
            ZonedDateTime evaluatedAt,
            Map<Long, Train> trainsById,
            Map<Long, Station> stationsById
    ) {
        int capacity = depot.getCapacity();
        long availableSpaces = Math.max(0, capacity - occupiedSpaces);
        int occupancyPercentage = percentage(occupiedSpaces, capacity);
        List<DepotMovementResponse> movements = plannedMovements.stream()
                .map(movement -> toMovementResponse(
                        movement, evaluatedAt, trainsById, stationsById
                ))
                .sorted(Comparator.comparing(DepotMovementResponse::scheduledAt)
                        .thenComparing(DepotMovementResponse::type)
                        .thenComparingInt(DepotMovementResponse::dutyNumber))
                .toList();
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
                fleetDistribution(assignedTrains, simulatedTrainsById),
                summarizeMovements(movements),
                movements
        );
    }

    private DepotMovementResponse toMovementResponse(
            PlannedDepotMovement movement,
            ZonedDateTime evaluatedAt,
            Map<Long, Train> trainsById,
            Map<Long, Station> stationsById
    ) {
        Train train = trainsById.get(movement.trainId());
        if (train == null) {
            throw new ServiceConfigurationException(
                    "Depot movement references an inactive train " + movement.trainCode()
            );
        }
        Station terminal = stationsById.get(movement.stationId());
        if (terminal == null) {
            throw new ServiceConfigurationException(
                    "Depot movement references an inactive station " + movement.stationCode()
            );
        }
        if (!train.getCode().equals(movement.trainCode())
                || !train.getAssignedLine().getId().equals(movement.lineId())
                || !train.getAssignedLine().getCode().equals(movement.lineCode())
                || !train.getHomeDepot().getId().equals(movement.depotId())
                || !train.getHomeDepot().getCode().equals(movement.depotCode())) {
            throw new ServiceConfigurationException(
                    "Persisted data and depot movement disagree for train " + movement.trainCode()
            );
        }
        boolean completed = movement.hasOccurredAt(evaluatedAt);
        return new DepotMovementResponse(
                movement.dutyNumber(),
                movement.movementType(),
                completed ? DepotMovementStatus.COMPLETED : DepotMovementStatus.SCHEDULED,
                movement.scheduledAt(),
                completed ? null : Duration.between(evaluatedAt, movement.scheduledAt()).toSeconds(),
                new DepotMovementTrainResponse(
                        train.getId(), train.getCode(), train.getModel().getSeries(), train.getFleetRole()
                ),
                new DepotMovementLineResponse(
                        train.getAssignedLine().getId(),
                        train.getAssignedLine().getCode(),
                        train.getAssignedLine().getName(),
                        train.getAssignedLine().getColor()
                ),
                new DepotOperationStationResponse(terminal.getId(), terminal.getCode(), terminal.getName())
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
                trainsInService,
                summarizeMovements(depots.stream().flatMap(depot -> depot.movements().stream()).toList())
        );
    }

    private DepotMovementsSummaryResponse summarizeMovements(List<DepotMovementResponse> movements) {
        long exits = movements.stream().filter(movement -> movement.type() == DepotMovementType.EXIT).count();
        long entries = movements.stream().filter(movement -> movement.type() == DepotMovementType.ENTRY).count();
        long completed = movements.stream()
                .filter(movement -> movement.status() == DepotMovementStatus.COMPLETED)
                .count();
        long scheduled = movements.size() - completed;
        ZonedDateTime nextMovementAt = movements.stream()
                .filter(movement -> movement.status() == DepotMovementStatus.SCHEDULED)
                .map(DepotMovementResponse::scheduledAt)
                .min(ZonedDateTime::compareTo)
                .orElse(null);
        return new DepotMovementsSummaryResponse(
                movements.size(), exits, entries, completed, scheduled, nextMovementAt
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
