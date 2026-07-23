package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
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
import com.transport.simulator.enums.ServicePeriodType;
import com.transport.simulator.enums.TrainPositionState;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.DepotRepository;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.repository.TransportLineRepository;
import com.transport.simulator.service.model.LineDutyPlan;
import com.transport.simulator.service.model.LineServiceOperationState;
import com.transport.simulator.service.model.RailwaySimulationState;
import com.transport.simulator.service.model.ResolvedLineServiceConfiguration;
import com.transport.simulator.service.model.ServicePeriodFleetPlan;
import com.transport.simulator.service.model.SimulatedLineState;
import com.transport.simulator.service.model.SimulatedTrainState;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationalSectionsConsistencyTests {

    private static final ZonedDateTime EVALUATED_AT = ZonedDateTime.of(
            2026, 7, 22, 8, 30, 0, 0, ZoneId.of("Europe/Madrid")
    );

    @Mock private RailwaySimulationStateService simulationStateService;
    @Mock private TransportLineRepository transportLineRepository;
    @Mock private StationRepository stationRepository;
    @Mock private LineStationRepository lineStationRepository;
    @Mock private TrainRepository trainRepository;
    @Mock private DepotRepository depotRepository;
    @Mock private DeviceRepository deviceRepository;

    private LineOperationsQueryService lineQueryService;
    private StationOperationsQueryService stationQueryService;
    private TrainOperationsQueryService trainQueryService;
    private DepotOperationsQueryService depotQueryService;

    @BeforeEach
    void setUp() {
        lineQueryService = new LineOperationsQueryService(
                simulationStateService, transportLineRepository, lineStationRepository
        );
        stationQueryService = new StationOperationsQueryService(
                simulationStateService, stationRepository, lineStationRepository, deviceRepository
        );
        trainQueryService = new TrainOperationsQueryService(
                simulationStateService, trainRepository, lineStationRepository
        );
        depotQueryService = new DepotOperationsQueryService(
                simulationStateService, depotRepository, trainRepository, stationRepository
        );
    }

    @Test
    void shouldKeepLinesStationsTrainsAndDepotsConsistentForTheSameSimulationSnapshot() {
        TransportLine line = line(1L, "L1", "Línea 1", "Roja");
        Station origin = station(10L, "ST010", "Plaza de la Mina");
        Station destination = station(11L, "ST011", "Los Molinos");
        Depot depot = depot(20L, "DEP-CM", "Cochera de Cuatro Caminos", origin);
        Train circulatingTrain = train(
                100L, "RMM-L1-9000-001", "9000", FleetRole.REGULAR_SERVICE, line, depot, 1
        );
        Train storedTrain = train(
                101L, "RMM-L1-7000-001", "7000", FleetRole.RESERVE, line, depot, 2
        );
        List<LineStation> route = List.of(
                stop(line, origin, 1, 90),
                stop(line, destination, 2, null)
        );
        List<SimulatedTrainState> trainStates = List.of(
                inServiceState(circulatingTrain, line, origin, destination),
                depotState(storedTrain, line, depot)
        );
        RailwaySimulationState simulation = simulation(line, trainStates);

        when(simulationStateService.getCurrentState()).thenReturn(simulation);
        when(transportLineRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(line));
        when(stationRepository.findAllByActiveTrueOrderByNameAsc())
                .thenReturn(List.of(origin, destination));
        when(lineStationRepository.findAllByActiveTrueOrderByLineCodeAscStationOrderAsc())
                .thenReturn(route);
        when(trainRepository.findAllByActiveTrueOrderByCodeAsc())
                .thenReturn(List.of(circulatingTrain, storedTrain));
        when(depotRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(depot));
        when(deviceRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of());

        var lineOperations = lineQueryService.getOperations();
        var stationOperations = stationQueryService.getOperations();
        var trainOperations = trainQueryService.getOperations();
        var depotOperations = depotQueryService.getOperations();

        assertThat(List.of(
                lineOperations.evaluatedAt(),
                stationOperations.evaluatedAt(),
                trainOperations.evaluatedAt(),
                depotOperations.evaluatedAt()
        )).containsOnly(EVALUATED_AT);
        assertThat(List.of(
                lineOperations.phase(),
                stationOperations.phase(),
                trainOperations.phase(),
                depotOperations.phase()
        )).containsOnly(ServiceOperationPhase.OPERATING);

        var lineResponse = lineOperations.lines().getFirst();
        assertThat(lineResponse.stationCount()).isEqualTo(stationOperations.stationCount());
        assertThat(lineResponse.stations()).extracting("code")
                .containsExactly("ST010", "ST011");
        assertThat(lineResponse.activeTrainCount())
                .isEqualTo(trainOperations.summary().trainsInService())
                .isEqualTo(depotOperations.summary().trainsInService());
        assertThat(stationOperations.stations())
                .allSatisfy(station -> assertThat(station.activeTrainCount())
                        .isEqualTo(lineResponse.activeTrainCount()));

        var lineTrain = lineResponse.trains().getFirst();
        var trainResponse = trainOperations.trains().stream()
                .filter(train -> train.code().equals(lineTrain.code()))
                .findFirst()
                .orElseThrow();
        assertThat(trainResponse.assignedLine().code()).isEqualTo(lineResponse.code());
        assertThat(trainResponse.serviceLocation().previousStation().code())
                .isEqualTo(lineTrain.previousStationCode());
        assertThat(trainResponse.serviceLocation().nextStation().code())
                .isEqualTo(lineTrain.nextStationCode());
        assertThat(trainResponse.serviceLocation().secondsUntilNextStation())
                .isEqualTo(lineTrain.secondsUntilNextStation());

        var destinationResponse = stationOperations.stations().stream()
                .filter(station -> station.code().equals(destination.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(destinationResponse.nextArrivals().getFirst().trainCode())
                .isEqualTo(circulatingTrain.getCode());
        assertThat(destinationResponse.nextArrivals().getFirst().secondsUntilArrival())
                .isEqualTo(lineTrain.secondsUntilNextStation());

        assertThat(trainOperations.summary().trainsInDepots())
                .isEqualTo(depotOperations.summary().occupiedSpaces());
        assertThat(depotOperations.depots().getFirst().occupiedSpaces()).isEqualTo(1);
        assertThat(trainOperations.trains()).filteredOn(train -> train.status() == TrainStatus.DEPOT)
                .extracting(train -> train.currentDepot().code())
                .containsExactly(depot.getCode());
    }

    private RailwaySimulationState simulation(
            TransportLine line,
            List<SimulatedTrainState> trainStates
    ) {
        Long lineId = line.getId();
        String lineCode = line.getCode();
        ResolvedLineServiceConfiguration configuration = mock(ResolvedLineServiceConfiguration.class);
        LineServiceOperationState operation = new LineServiceOperationState(
                lineId,
                lineCode,
                ServiceOperationPhase.OPERATING,
                Optional.of(configuration),
                12_600,
                57_600
        );
        ServicePeriodFleetPlan period = new ServicePeriodFleetPlan(
                "REGULAR",
                ServicePeriodType.REGULAR,
                EVALUATED_AT.minusHours(1),
                EVALUATED_AT.plusHours(1),
                300,
                1
        );
        LineDutyPlan dutyPlan = mock(LineDutyPlan.class);
        when(dutyPlan.lineId()).thenReturn(lineId);
        when(dutyPlan.serviceStartsAt()).thenReturn(EVALUATED_AT.withHour(5).withMinute(0));
        when(dutyPlan.serviceEndsAt()).thenReturn(EVALUATED_AT.plusDays(1).withHour(0).withMinute(30));
        when(dutyPlan.roundTripSeconds()).thenReturn(3600L);
        when(dutyPlan.periods()).thenReturn(List.of(period));

        return new RailwaySimulationState(
                EVALUATED_AT,
                ServiceOperationPhase.OPERATING,
                1,
                List.of(new SimulatedLineState(operation, Optional.of(dutyPlan))),
                trainStates,
                List.of()
        );
    }

    private SimulatedTrainState inServiceState(
            Train train,
            TransportLine line,
            Station previous,
            Station next
    ) {
        return new SimulatedTrainState(
                train.getId(), train.getCode(), train.getModel().getSeries(), train.getFleetRole(),
                TrainStatus.IN_SERVICE, line.getId(), line.getCode(), line.getId(), line.getCode(),
                null, null, 1, TrainPositionState.BETWEEN_STATIONS, ServiceDirection.OUTBOUND,
                null, null, previous.getId(), previous.getCode(), next.getId(), next.getCode(),
                50, 45L, EVALUATED_AT.plusSeconds(45)
        );
    }

    private SimulatedTrainState depotState(Train train, TransportLine line, Depot depot) {
        return new SimulatedTrainState(
                train.getId(), train.getCode(), train.getModel().getSeries(), train.getFleetRole(),
                TrainStatus.DEPOT, line.getId(), line.getCode(), null, null, depot.getId(), depot.getCode(),
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    private Train train(
            Long id,
            String code,
            String series,
            FleetRole fleetRole,
            TransportLine line,
            Depot depot,
            int dispatchOrder
    ) {
        TrainModel model = mock(TrainModel.class);
        when(model.getSeries()).thenReturn(series);
        lenient().when(model.getManufacturer()).thenReturn("Macegocia Rail");
        lenient().when(model.getModelName()).thenReturn("Serie " + series);
        lenient().when(model.getCarCount()).thenReturn(6);
        lenient().when(model.getPassengerCapacity()).thenReturn(900);
        lenient().when(model.getMaximumSpeedKmh()).thenReturn(80);
        Train train = mock(Train.class);
        when(train.getId()).thenReturn(id);
        when(train.getCode()).thenReturn(code);
        when(train.getModel()).thenReturn(model);
        when(train.getFleetRole()).thenReturn(fleetRole);
        when(train.getAssignedLine()).thenReturn(line);
        when(train.getHomeDepot()).thenReturn(depot);
        lenient().when(train.getDispatchOrder()).thenReturn(dispatchOrder);
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
        when(depot.getCapacity()).thenReturn(4);
        when(depot.getTrackCount()).thenReturn(2);
        when(depot.getTrainsPerTrack()).thenReturn(2);
        return depot;
    }

    private LineStation stop(
            TransportLine line,
            Station station,
            int stationOrder,
            Integer travelSecondsToNext
    ) {
        LineStation stop = mock(LineStation.class);
        when(stop.getLine()).thenReturn(line);
        when(stop.getStation()).thenReturn(station);
        when(stop.getStationOrder()).thenReturn(stationOrder);
        lenient().when(stop.getTravelSecondsToNext()).thenReturn(travelSecondsToNext);
        lenient().when(stop.getDwellSeconds()).thenReturn(20);
        return stop;
    }
}
