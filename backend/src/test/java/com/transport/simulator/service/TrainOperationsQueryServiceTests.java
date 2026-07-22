package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Depot;
import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Train;
import com.transport.simulator.entity.TrainModel;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.TrainPositionState;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.TrainRepository;
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
class TrainOperationsQueryServiceTests {

    private static final ZonedDateTime EVALUATED_AT = ZonedDateTime.of(
            2026, 7, 22, 8, 30, 0, 0, ZoneId.of("Europe/Madrid")
    );

    @Mock private RailwaySimulationStateService simulationStateService;
    @Mock private TrainRepository trainRepository;
    @Mock private LineStationRepository lineStationRepository;
    private TrainOperationsQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new TrainOperationsQueryService(
                simulationStateService, trainRepository, lineStationRepository
        );
    }

    @Test
    void shouldExposeFleetSummaryAndCurrentLocationForEveryActiveTrain() {
        TransportLine line = line(1L, "L1", "Línea 1", "Roja");
        Station first = station(10L, "ST010", "Plaza de la Mina");
        Station second = station(11L, "ST011", "Las Fuentes");
        Depot depot = depot(20L, "DEP01", "Cocheras Norte", first);
        Train regular = train(100L, "T-9001", "9000", FleetRole.REGULAR_SERVICE, line, depot, 1);
        Train reserve = train(101L, "T-7001", "7000", FleetRole.RESERVE, line, depot, 2);
        RailwaySimulationState simulation = mock(RailwaySimulationState.class);
        List<SimulatedTrainState> simulatedTrains = List.of(
                inServiceState(regular, line, first, second),
                depotState(reserve, line, depot)
        );
        List<LineStation> route = List.of(stop(line, first), stop(line, second));

        when(simulation.evaluatedAt()).thenReturn(EVALUATED_AT);
        when(simulation.phase()).thenReturn(ServiceOperationPhase.OPERATING);
        when(simulation.trains()).thenReturn(simulatedTrains);
        when(simulationStateService.getCurrentState()).thenReturn(simulation);
        when(trainRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(regular, reserve));
        when(lineStationRepository.findAllByActiveTrueOrderByLineCodeAscStationOrderAsc())
                .thenReturn(route);

        var response = queryService.getOperations();

        assertThat(response.evaluatedAt()).isEqualTo(EVALUATED_AT);
        assertThat(response.summary().activeFleet()).isEqualTo(2);
        assertThat(response.summary().trainsInService()).isEqualTo(1);
        assertThat(response.summary().trainsInDepots()).isEqualTo(1);
        assertThat(response.summary().byRole()).containsEntry(FleetRole.REGULAR_SERVICE, 1L)
                .containsEntry(FleetRole.RESERVE, 1L)
                .containsEntry(FleetRole.HISTORIC, 0L);
        assertThat(response.summary().bySeries()).containsEntry("9000", 1L).containsEntry("7000", 1L);

        var circulating = response.trains().getFirst();
        assertThat(circulating.code()).isEqualTo("T-9001");
        assertThat(circulating.currentDepot()).isNull();
        assertThat(circulating.serviceLocation().direction()).isEqualTo(ServiceDirection.OUTBOUND);
        assertThat(circulating.serviceLocation().destination().code()).isEqualTo("ST011");
        assertThat(circulating.serviceLocation().previousStation().code()).isEqualTo("ST010");
        assertThat(circulating.serviceLocation().nextStation().code()).isEqualTo("ST011");
        assertThat(circulating.serviceLocation().secondsUntilNextStation()).isEqualTo(65);

        var stored = response.trains().get(1);
        assertThat(stored.fleetRole()).isEqualTo(FleetRole.RESERVE);
        assertThat(stored.serviceLocation()).isNull();
        assertThat(stored.currentDepot().code()).isEqualTo("DEP01");
    }

    private SimulatedTrainState inServiceState(Train train, TransportLine line, Station first, Station second) {
        return new SimulatedTrainState(
                train.getId(), train.getCode(), train.getModel().getSeries(), train.getFleetRole(),
                TrainStatus.IN_SERVICE, line.getId(), line.getCode(), line.getId(), line.getCode(),
                null, null, 1, TrainPositionState.BETWEEN_STATIONS, ServiceDirection.OUTBOUND,
                null, null, first.getId(), first.getCode(), second.getId(), second.getCode(),
                40, 65L, EVALUATED_AT.plusSeconds(65)
        );
    }

    private SimulatedTrainState depotState(Train train, TransportLine line, Depot depot) {
        return new SimulatedTrainState(
                train.getId(), train.getCode(), train.getModel().getSeries(), train.getFleetRole(),
                TrainStatus.DEPOT, line.getId(), line.getCode(), null, null, depot.getId(), depot.getCode(),
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    private Train train(Long id, String code, String series, FleetRole role, TransportLine line, Depot depot, int order) {
        TrainModel model = mock(TrainModel.class);
        when(model.getManufacturer()).thenReturn("Macegocia Rail");
        when(model.getModelName()).thenReturn("Serie " + series);
        when(model.getSeries()).thenReturn(series);
        when(model.getCarCount()).thenReturn(6);
        when(model.getPassengerCapacity()).thenReturn(900);
        when(model.getMaximumSpeedKmh()).thenReturn(80);
        Train train = mock(Train.class);
        when(train.getId()).thenReturn(id);
        when(train.getCode()).thenReturn(code);
        when(train.getModel()).thenReturn(model);
        when(train.getFleetRole()).thenReturn(role);
        when(train.getAssignedLine()).thenReturn(line);
        when(train.getHomeDepot()).thenReturn(depot);
        when(train.getDispatchOrder()).thenReturn(order);
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

    private Depot depot(Long id, String code, String name, Station station) {
        Depot depot = mock(Depot.class);
        when(depot.getId()).thenReturn(id);
        when(depot.getCode()).thenReturn(code);
        when(depot.getName()).thenReturn(name);
        when(depot.getStation()).thenReturn(station);
        return depot;
    }

    private LineStation stop(TransportLine line, Station station) {
        LineStation stop = mock(LineStation.class);
        when(stop.getLine()).thenReturn(line);
        when(stop.getStation()).thenReturn(station);
        return stop;
    }
}
