package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Depot;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Train;
import com.transport.simulator.entity.TrainModel;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.DepotMovementStatus;
import com.transport.simulator.enums.DepotMovementType;
import com.transport.simulator.enums.DepotOperationStatus;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.TrainPositionState;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.DepotRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.service.model.PlannedDepotMovement;
import com.transport.simulator.service.model.RailwaySimulationState;
import com.transport.simulator.service.model.SimulatedTrainState;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepotOperationsQueryServiceTests {

    private static final ZonedDateTime EVALUATED_AT = ZonedDateTime.of(
            2026, 7, 22, 8, 30, 0, 0, ZoneId.of("Europe/Madrid")
    );

    @Mock private RailwaySimulationStateService simulationStateService;
    @Mock private DepotRepository depotRepository;
    @Mock private TrainRepository trainRepository;
    @Mock private StationRepository stationRepository;
    private DepotOperationsQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new DepotOperationsQueryService(
                simulationStateService, depotRepository, trainRepository, stationRepository
        );
    }

    @Test
    void shouldCalculateOccupancyAndClassifyCompletedAndScheduledMovements() {
        Station terminal = station(10L, "ST010", "Plaza de la Mina");
        TransportLine line = line(1L, "L1", "Línea 1", "Roja");
        Depot depot = depot(20L, "DEP-LF-A", "Cochera de Las Fuentes - Sector A", terminal, 2, 1, 2);
        Train regular = train(100L, "T-9001", "9000", FleetRole.REGULAR_SERVICE, line, depot);
        Train reserve = train(101L, "T-7001", "7000", FleetRole.RESERVE, line, depot);
        List<SimulatedTrainState> trainStates = List.of(
                inServiceState(regular, line, terminal), depotState(reserve, line, depot)
        );
        List<PlannedDepotMovement> movements = List.of(
                movement(regular, line, depot, terminal, DepotMovementType.EXIT, EVALUATED_AT.minusMinutes(30)),
                movement(regular, line, depot, terminal, DepotMovementType.ENTRY, EVALUATED_AT.plusMinutes(30))
        );
        RailwaySimulationState simulation = mock(RailwaySimulationState.class);

        when(simulation.evaluatedAt()).thenReturn(EVALUATED_AT);
        when(simulation.phase()).thenReturn(ServiceOperationPhase.OPERATING);
        when(simulation.trains()).thenReturn(trainStates);
        when(simulation.depotMovements()).thenReturn(movements);
        when(simulationStateService.getCurrentState()).thenReturn(simulation);
        when(trainRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(regular, reserve));
        when(stationRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(terminal));
        when(depotRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(depot));

        var response = queryService.getOperations();
        var result = response.depots().getFirst();

        assertThat(response.summary().occupiedSpaces()).isEqualTo(1);
        assertThat(response.summary().availableSpaces()).isEqualTo(1);
        assertThat(response.summary().occupancyPercentage()).isEqualTo(50);
        assertThat(response.summary().trainsInService()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(DepotOperationStatus.AVAILABLE);
        assertThat(result.fleet().byRole()).containsEntry(FleetRole.REGULAR_SERVICE, 1L)
                .containsEntry(FleetRole.RESERVE, 1L);
        assertThat(result.fleet().bySeries()).containsEntry("9000", 1L).containsEntry("7000", 1L);
        assertThat(result.movements()).extracting("type", "status", "secondsUntilMovement")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(DepotMovementType.EXIT, DepotMovementStatus.COMPLETED, null),
                        org.assertj.core.groups.Tuple.tuple(DepotMovementType.ENTRY, DepotMovementStatus.SCHEDULED, 1800L)
                );
        assertThat(result.movementsSummary().entries()).isEqualTo(1);
        assertThat(result.movementsSummary().exits()).isEqualTo(1);
        assertThat(result.movementsSummary().nextMovementAt()).isEqualTo(EVALUATED_AT.plusMinutes(30));
        assertThat(result.movements().getLast().terminal().name()).isEqualTo("Plaza de la Mina");
    }

    private PlannedDepotMovement movement(
            Train train, TransportLine line, Depot depot, Station terminal,
            DepotMovementType type, ZonedDateTime scheduledAt
    ) {
        return new PlannedDepotMovement(
                1, train.getId(), train.getCode(), line.getId(), line.getCode(),
                depot.getId(), depot.getCode(), terminal.getId(), terminal.getCode(), type, scheduledAt
        );
    }

    private SimulatedTrainState inServiceState(Train train, TransportLine line, Station station) {
        return new SimulatedTrainState(
                train.getId(), train.getCode(), train.getModel().getSeries(), train.getFleetRole(),
                TrainStatus.IN_SERVICE, line.getId(), line.getCode(), line.getId(), line.getCode(),
                null, null, 1, TrainPositionState.AT_STATION, ServiceDirection.OUTBOUND,
                station.getId(), station.getCode(), station.getId(), station.getCode(),
                station.getId(), station.getCode(), 0, 20L, EVALUATED_AT.plusSeconds(20)
        );
    }

    private SimulatedTrainState depotState(Train train, TransportLine line, Depot depot) {
        return new SimulatedTrainState(
                train.getId(), train.getCode(), train.getModel().getSeries(), train.getFleetRole(),
                TrainStatus.DEPOT, line.getId(), line.getCode(), null, null, depot.getId(), depot.getCode(),
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    private Train train(Long id, String code, String series, FleetRole role, TransportLine line, Depot depot) {
        TrainModel model = mock(TrainModel.class);
        when(model.getSeries()).thenReturn(series);
        Train train = mock(Train.class);
        when(train.getId()).thenReturn(id);
        when(train.getCode()).thenReturn(code);
        when(train.getModel()).thenReturn(model);
        when(train.getFleetRole()).thenReturn(role);
        when(train.getAssignedLine()).thenReturn(line);
        when(train.getHomeDepot()).thenReturn(depot);
        return train;
    }

    private TransportLine line(Long id, String code, String name, String color) {
        TransportLine line = mock(TransportLine.class);
        when(line.getId()).thenReturn(id);
        when(line.getCode()).thenReturn(code);
        when(line.getName()).thenReturn(name);
        when(line.getColor()).thenReturn(color);
        return line;
    }

    private Station station(Long id, String code, String name) {
        Station station = mock(Station.class);
        when(station.getId()).thenReturn(id);
        when(station.getCode()).thenReturn(code);
        when(station.getName()).thenReturn(name);
        return station;
    }

    private Depot depot(Long id, String code, String name, Station station, int capacity, int tracks, int perTrack) {
        Depot depot = mock(Depot.class);
        when(depot.getId()).thenReturn(id);
        when(depot.getCode()).thenReturn(code);
        when(depot.getName()).thenReturn(name);
        when(depot.getStation()).thenReturn(station);
        when(depot.getCapacity()).thenReturn(capacity);
        when(depot.getTrackCount()).thenReturn(tracks);
        when(depot.getTrainsPerTrack()).thenReturn(perTrack);
        return depot;
    }
}
