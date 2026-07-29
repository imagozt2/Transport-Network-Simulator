package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.StationOperationStatus;
import com.transport.simulator.enums.TrainPositionState;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.projection.StationDeviceSummaryProjection;
import com.transport.simulator.service.model.LineDutyPlan;
import com.transport.simulator.service.model.LineServiceOperationState;
import com.transport.simulator.service.model.RailwaySimulationState;
import com.transport.simulator.service.model.ResolvedLineServiceConfiguration;
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
class StationOperationsQueryServiceTests {

    private static final ZonedDateTime EVALUATED_AT = ZonedDateTime.of(
            2026, 7, 22, 8, 30, 0, 0, ZoneId.of("Europe/Madrid")
    );

    @Mock
    private RailwaySimulationStateService simulationStateService;
    @Mock
    private StationRepository stationRepository;
    @Mock
    private LineStationRepository lineStationRepository;
    @Mock
    private DeviceRepository deviceRepository;

    private StationOperationsQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new StationOperationsQueryService(
                simulationStateService,
                stationRepository,
                lineStationRepository,
                deviceRepository
        );
    }

    @Test
    void shouldBuildStationStatesAndPreciseArrivalsAcrossATerminalReversal() {
        Station stationA = station(1L, "STA", "Estación A");
        Station stationB = station(2L, "STB", "Estación B");
        Station stationC = station(3L, "STC", "Estación C");
        TransportLine line = line(10L, "L3", "Línea 3", "Amarilla");
        List<LineStation> route = List.of(
                stop(line, stationA, 1, 60, 20),
                stop(line, stationB, 2, 120, 20),
                stop(line, stationC, 3, null, 20)
        );
        SimulatedTrainState movingTrain = train(
                90L, "T-9001", TrainPositionState.BETWEEN_STATIONS,
                ServiceDirection.OUTBOUND, null, stationA, stationB, 30
        );
        SimulatedTrainState stoppedTrain = train(
                91L, "T-9002", TrainPositionState.AT_STATION,
                ServiceDirection.OUTBOUND, stationB, stationB, stationC, 140
        );
        StationDeviceSummaryProjection failedDevice = mock(StationDeviceSummaryProjection.class);
        StationDeviceSummaryProjection entryValidator = mock(StationDeviceSummaryProjection.class);
        StationDeviceSummaryProjection exitValidator = mock(StationDeviceSummaryProjection.class);
        Long stationAId = stationA.getId();
        Long stationBId = stationB.getId();
        when(failedDevice.getStationId()).thenReturn(stationAId);
        when(failedDevice.getType()).thenReturn(DeviceType.TICKET_MACHINE);
        when(failedDevice.getStatus()).thenReturn(DeviceStatus.ERROR);
        when(failedDevice.getTotal()).thenReturn(1L);
        when(entryValidator.getStationId()).thenReturn(stationBId);
        when(entryValidator.getType()).thenReturn(DeviceType.ENTRY_VALIDATOR);
        when(entryValidator.getStatus()).thenReturn(DeviceStatus.ONLINE);
        when(entryValidator.getTotal()).thenReturn(2L);
        when(exitValidator.getStationId()).thenReturn(stationBId);
        when(exitValidator.getType()).thenReturn(DeviceType.EXIT_VALIDATOR);
        when(exitValidator.getStatus()).thenReturn(DeviceStatus.ONLINE);
        when(exitValidator.getTotal()).thenReturn(3L);

        RailwaySimulationState simulation = simulation(
                line,
                List.of(movingTrain, stoppedTrain)
        );
        when(simulationStateService.getCurrentState()).thenReturn(simulation);
        when(stationRepository.findAllByActiveTrueOrderByNameAsc())
                .thenReturn(List.of(stationA, stationB, stationC));
        when(lineStationRepository.findAllByActiveTrueOrderByLineCodeAscStationOrderAsc())
                .thenReturn(route);
        when(deviceRepository.summarizeActiveDevicesByStation())
                .thenReturn(List.of(failedDevice, entryValidator, exitValidator));

        var response = queryService.getOperations();

        assertThat(response.stationCount()).isEqualTo(3);
        assertThat(response.activeStationCount()).isEqualTo(3);
        assertThat(response.summary()).satisfies(summary -> {
            assertThat(summary.stationCount()).isEqualTo(3);
            assertThat(summary.activeStationCount()).isEqualTo(3);
            assertThat(summary.transferStationCount()).isZero();
            assertThat(summary.ticketMachineCount()).isEqualTo(1);
            assertThat(summary.entryValidatorCount()).isEqualTo(2);
            assertThat(summary.exitValidatorCount()).isEqualTo(3);
        });
        assertThat(response.stations().getFirst().status()).isEqualTo(StationOperationStatus.CRITICAL);

        var stationBResponse = response.stations().get(1);
        assertThat(stationBResponse.status()).isEqualTo(StationOperationStatus.NORMAL);
        assertThat(stationBResponse.activeLineCount()).isEqualTo(1);
        assertThat(stationBResponse.activeTrainCount()).isEqualTo(2);
        assertThat(stationBResponse.lines()).hasSize(1);
        assertThat(stationBResponse.lines().getFirst().activeTrainCount()).isEqualTo(2);
        assertThat(stationBResponse.lines().getFirst().directions().stream()
                .mapToInt(direction -> direction.activeTrainCount())
                .sum()).isEqualTo(stationBResponse.lines().getFirst().activeTrainCount());
        assertThat(stationBResponse.lines().getFirst().directions()).extracting(
                "direction", "activeTrainCount", "destination.code"
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ServiceDirection.OUTBOUND, 2, "STC"),
                org.assertj.core.groups.Tuple.tuple(ServiceDirection.INBOUND, 0, "STA")
        );
        assertThat(stationBResponse.nextArrivals()).extracting(
                "trainCode", "secondsUntilArrival", "stationsAway", "direction", "atStation"
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple("T-9002", 0L, 0, ServiceDirection.OUTBOUND, true),
                org.assertj.core.groups.Tuple.tuple("T-9001", 30L, 1, ServiceDirection.OUTBOUND, false)
        );

        var arrivalBackAtStationA = response.stations().getFirst().nextArrivals().getFirst();
        assertThat(arrivalBackAtStationA.trainCode()).isEqualTo("T-9002");
        assertThat(arrivalBackAtStationA.secondsUntilArrival()).isEqualTo(360);
        assertThat(arrivalBackAtStationA.direction()).isEqualTo(ServiceDirection.INBOUND);
        assertThat(arrivalBackAtStationA.destination().code()).isEqualTo("STA");
        assertThat(arrivalBackAtStationA.estimatedArrivalAt()).isEqualTo(EVALUATED_AT.plusSeconds(360));
    }

    private RailwaySimulationState simulation(TransportLine line, List<SimulatedTrainState> trains) {
        Long lineId = line.getId();
        String lineCode = line.getCode();
        ResolvedLineServiceConfiguration configuration = mock(ResolvedLineServiceConfiguration.class);
        LineServiceOperationState operation = new LineServiceOperationState(
                lineId, lineCode, ServiceOperationPhase.OPERATING,
                Optional.of(configuration), 1, 1
        );
        LineDutyPlan dutyPlan = mock(LineDutyPlan.class);
        when(dutyPlan.lineId()).thenReturn(lineId);
        return new RailwaySimulationState(
                EVALUATED_AT,
                ServiceOperationPhase.OPERATING,
                1,
                List.of(new SimulatedLineState(operation, Optional.of(dutyPlan))),
                trains,
                List.of()
        );
    }

    private SimulatedTrainState train(
            Long id,
            String code,
            TrainPositionState positionState,
            ServiceDirection direction,
            Station current,
            Station previous,
            Station next,
            long secondsUntilNext
    ) {
        return new SimulatedTrainState(
                id, code, "9000", FleetRole.REGULAR_SERVICE, TrainStatus.IN_SERVICE,
                10L, "L3", 10L, "L3", null, null, Math.toIntExact(id),
                positionState, direction,
                current == null ? null : current.getId(), current == null ? null : current.getCode(),
                previous.getId(), previous.getCode(), next.getId(), next.getCode(),
                50, secondsUntilNext, EVALUATED_AT.plusSeconds(secondsUntilNext)
        );
    }

    private Station station(Long id, String code, String name) {
        Station station = mock(Station.class);
        when(station.getId()).thenReturn(id);
        when(station.getCode()).thenReturn(code);
        when(station.getName()).thenReturn(name);
        return station;
    }

    private TransportLine line(Long id, String code, String name, String color) {
        TransportLine line = mock(TransportLine.class);
        when(line.getId()).thenReturn(id);
        when(line.getCode()).thenReturn(code);
        when(line.getName()).thenReturn(name);
        when(line.getColor()).thenReturn(color);
        return line;
    }

    private LineStation stop(
            TransportLine line,
            Station station,
            int order,
            Integer travelSeconds,
            int dwellSeconds
    ) {
        LineStation stop = mock(LineStation.class);
        when(stop.getLine()).thenReturn(line);
        when(stop.getStation()).thenReturn(station);
        when(stop.getStationOrder()).thenReturn(order);
        lenient().when(stop.getTravelSecondsToNext()).thenReturn(travelSeconds);
        lenient().when(stop.getDwellSeconds()).thenReturn(dwellSeconds);
        return stop;
    }
}
