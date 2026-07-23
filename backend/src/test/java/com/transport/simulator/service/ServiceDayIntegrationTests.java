package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Depot;
import com.transport.simulator.entity.LineServiceLevel;
import com.transport.simulator.entity.ServicePeriod;
import com.transport.simulator.entity.Train;
import com.transport.simulator.entity.TrainModel;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.DepotMovementType;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.OperatingDayType;
import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.ServicePeriodType;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.LineServiceLevelRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.repository.TransportLineRepository;
import com.transport.simulator.service.model.LineDepotConfiguration;
import com.transport.simulator.service.model.ResolvedLineServiceConfiguration;
import com.transport.simulator.service.model.RouteStopConfiguration;
import com.transport.simulator.service.model.RailwaySimulationState;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceDayIntegrationTests {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Europe/Madrid");
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 7, 21);
    private static final String CALENDAR_CODE = "WEEKDAY_STANDARD";

    private final TransportLineRepository lineRepository = mock(TransportLineRepository.class);
    private final ServiceConfigurationService configurationService =
            mock(ServiceConfigurationService.class);
    private final LineServiceLevelRepository serviceLevelRepository =
            mock(LineServiceLevelRepository.class);
    private final TrainRepository trainRepository = mock(TrainRepository.class);

    private RailwaySimulationStateService simulationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.system(SERVICE_ZONE);
        TransportLine line = line();
        List<Train> trains = List.of(
                train(1L, "RMM-L1-9000-001", line, depot(201L, "DEP-A")),
                train(2L, "RMM-L1-9000-002", line, depot(202L, "DEP-C")),
                train(3L, "RMM-L1-9000-003", line, depot(201L, "DEP-A")),
                train(4L, "RMM-L1-9000-004", line, depot(202L, "DEP-C"))
        );
        List<LineServiceLevel> levels = serviceLevels();

        when(lineRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(line));
        when(configurationService.findForLineAt(any(), any()))
                .thenAnswer(invocation -> configurationAt(invocation.getArgument(1)));
        when(serviceLevelRepository
                .findAllByLineIdAndServicePeriodServiceCalendarCodeAndActiveTrueAndServicePeriodActiveTrueOrderByServicePeriodPeriodOrderAsc(
                        1L,
                        CALENDAR_CODE
                ))
                .thenReturn(levels);
        when(trainRepository
                .findAllByAssignedLineIdAndFleetRoleAndModelSeriesAndActiveTrueAndModelActiveTrueAndHomeDepotActiveTrueOrderByDispatchOrderAscCodeAsc(
                        1L,
                        FleetRole.REGULAR_SERVICE,
                        "9000"
                ))
                .thenReturn(trains);
        when(trainRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(trains);

        ServiceOperationStateService operationStateService = new ServiceOperationStateService(
                clock,
                lineRepository,
                configurationService
        );
        TrainDutyPlanningService planningService = new TrainDutyPlanningService(
                clock,
                operationStateService,
                serviceLevelRepository,
                trainRepository
        );
        simulationService = new RailwaySimulationStateService(
                clock,
                operationStateService,
                planningService,
                trainRepository
        );
    }

    @Test
    void shouldKeepTheNetworkClosedAndTheFleetInDepotsBeforeService() {
        RailwaySimulationState state = simulationService.getStateAt(at(SERVICE_DATE, 4, 30));

        assertThat(state.phase()).isEqualTo(ServiceOperationPhase.CLOSED);
        assertThat(state.activeLineCount()).isZero();
        assertThat(state.trains()).allMatch(train -> train.status() == TrainStatus.DEPOT);
        assertThat(state.depotMovements()).isEmpty();
    }

    @Test
    void shouldDispatchTheFleetWhenTheServiceStarts() {
        RailwaySimulationState state = simulationService.getStateAt(at(SERVICE_DATE, 5, 5));

        assertThat(state.phase()).isEqualTo(ServiceOperationPhase.STARTING);
        assertThat(state.activeLineCount()).isEqualTo(1);
        assertThat(state.trains()).anyMatch(train -> train.status() == TrainStatus.IN_SERVICE);
        assertThat(state.depotMovements())
                .anyMatch(movement -> movement.movementType() == DepotMovementType.EXIT);
    }

    @Test
    void shouldIncreaseTheOperatingFleetForTheMorningPeakAndReduceItAfterwards() {
        RailwaySimulationState peak = simulationService.getStateAt(at(SERVICE_DATE, 8, 0));
        RailwaySimulationState regular = simulationService.getStateAt(at(SERVICE_DATE, 12, 0));

        long peakFleet = inServiceTrainCount(peak);
        long regularFleet = inServiceTrainCount(regular);

        assertThat(peak.phase()).isEqualTo(ServiceOperationPhase.OPERATING);
        assertThat(regular.phase()).isEqualTo(ServiceOperationPhase.OPERATING);
        assertThat(peakFleet).isGreaterThan(regularFleet);
        assertThat(peak.lines().getFirst().operation().configuration()
                .orElseThrow().headwaySeconds())
                .isLessThan(regular.lines().getFirst().operation().configuration()
                        .orElseThrow().headwaySeconds());
        assertThat(regular.depotMovements())
                .anyMatch(movement -> movement.movementType() == DepotMovementType.ENTRY);
    }

    @Test
    void shouldRunTheEndingPeriodAcrossMidnightAndCloseAfterwards() {
        RailwaySimulationState ending = simulationService.getStateAt(
                at(SERVICE_DATE.plusDays(1), 0, 15)
        );
        RailwaySimulationState closed = simulationService.getStateAt(
                at(SERVICE_DATE.plusDays(1), 0, 31)
        );

        assertThat(ending.phase()).isEqualTo(ServiceOperationPhase.ENDING);
        assertThat(ending.activeLineCount()).isEqualTo(1);
        assertThat(closed.phase()).isEqualTo(ServiceOperationPhase.CLOSED);
        assertThat(closed.activeLineCount()).isZero();
        assertThat(closed.trains()).allMatch(train -> train.status() == TrainStatus.DEPOT);
    }

    private Optional<ResolvedLineServiceConfiguration> configurationAt(
            ZonedDateTime evaluatedAt
    ) {
        LocalTime time = evaluatedAt.toLocalTime();
        LocalDate serviceDate = evaluatedAt.toLocalDate();
        if (time.isBefore(LocalTime.of(0, 30))) {
            serviceDate = serviceDate.minusDays(1);
        } else if (time.isBefore(LocalTime.of(5, 0))) {
            return Optional.empty();
        }

        if (time.isAfter(LocalTime.of(0, 30)) && time.isBefore(LocalTime.of(5, 0))) {
            return Optional.empty();
        }

        if (serviceDate.isAfter(SERVICE_DATE)
                || serviceDate.isBefore(SERVICE_DATE)
                || (evaluatedAt.toLocalDate().isAfter(SERVICE_DATE)
                && time.isAfter(LocalTime.of(0, 30)))) {
            return Optional.empty();
        }

        ServicePeriodType type;
        String code;
        LocalTime periodStart;
        LocalTime periodEnd;
        if (!evaluatedAt.toLocalDate().equals(serviceDate)) {
            type = ServicePeriodType.SERVICE_END;
            code = "SERVICE_END";
            periodStart = LocalTime.of(23, 30);
            periodEnd = LocalTime.of(0, 30);
        } else if (time.isBefore(LocalTime.of(6, 0))) {
            type = ServicePeriodType.SERVICE_START;
            code = "SERVICE_START";
            periodStart = LocalTime.of(5, 0);
            periodEnd = LocalTime.of(6, 0);
        } else if (time.isBefore(LocalTime.of(10, 0))) {
            type = ServicePeriodType.PEAK;
            code = "MORNING_PEAK";
            periodStart = LocalTime.of(6, 0);
            periodEnd = LocalTime.of(10, 0);
        } else if (time.isBefore(LocalTime.of(23, 30))) {
            type = ServicePeriodType.REGULAR;
            code = "REGULAR";
            periodStart = LocalTime.of(10, 0);
            periodEnd = LocalTime.of(23, 30);
        } else {
            type = ServicePeriodType.SERVICE_END;
            code = "SERVICE_END";
            periodStart = LocalTime.of(23, 30);
            periodEnd = LocalTime.of(0, 30);
        }

        return Optional.of(configuration(
                serviceDate,
                code,
                type,
                periodStart,
                periodEnd
        ));
    }

    private ResolvedLineServiceConfiguration configuration(
            LocalDate serviceDate,
            String periodCode,
            ServicePeriodType periodType,
            LocalTime periodStart,
            LocalTime periodEnd
    ) {
        return new ResolvedLineServiceConfiguration(
                1L,
                "L1",
                serviceDate,
                CALENDAR_CODE,
                OperatingDayType.WEEKDAY,
                LocalTime.of(5, 0),
                LocalTime.of(0, 30),
                periodCode,
                periodType,
                periodStart,
                periodEnd,
                headwayFor(periodType),
                route(),
                depots()
        );
    }

    private List<LineServiceLevel> serviceLevels() {
        return List.of(
                level("SERVICE_START", ServicePeriodType.SERVICE_START, 5, 6, 240),
                level("MORNING_PEAK", ServicePeriodType.PEAK, 6, 10, 120),
                level("REGULAR", ServicePeriodType.REGULAR, 10, 23, 240),
                level("SERVICE_END", ServicePeriodType.SERVICE_END, 23, 0, 300)
        );
    }

    private int headwayFor(ServicePeriodType periodType) {
        return switch (periodType) {
            case PEAK -> 120;
            case SERVICE_END -> 300;
            default -> 240;
        };
    }

    private List<RouteStopConfiguration> route() {
        return List.of(
                new RouteStopConfiguration(101L, "STA", "Terminal A", 1, 80, 20),
                new RouteStopConfiguration(102L, "STB", "Estación B", 2, 80, 20),
                new RouteStopConfiguration(103L, "STC", "Terminal C", 3, null, 20)
        );
    }

    private List<LineDepotConfiguration> depots() {
        return List.of(
                new LineDepotConfiguration(
                        201L, "DEP-A", "Cochera A", 101L, "STA",
                        101L, "STA", 1, true, true
                ),
                new LineDepotConfiguration(
                        202L, "DEP-C", "Cochera C", 103L, "STC",
                        103L, "STC", 2, true, true
                )
        );
    }

    private LineServiceLevel level(
            String code,
            ServicePeriodType type,
            int startHour,
            int endHour,
            int headway
    ) {
        ServicePeriod period = mock(ServicePeriod.class);
        when(period.getCode()).thenReturn(code);
        when(period.getPeriodType()).thenReturn(type);
        when(period.getStartTime()).thenReturn(LocalTime.of(startHour, 0));
        when(period.getEndTime()).thenReturn(LocalTime.of(endHour, type == ServicePeriodType.SERVICE_END ? 30 : 0));
        LineServiceLevel level = mock(LineServiceLevel.class);
        when(level.getServicePeriod()).thenReturn(period);
        when(level.getHeadwaySeconds()).thenReturn(headway);
        return level;
    }

    private TransportLine line() {
        TransportLine line = mock(TransportLine.class);
        when(line.getId()).thenReturn(1L);
        when(line.getCode()).thenReturn("L1");
        return line;
    }

    private Depot depot(long id, String code) {
        Depot depot = mock(Depot.class);
        when(depot.getId()).thenReturn(id);
        when(depot.getCode()).thenReturn(code);
        return depot;
    }

    private Train train(long id, String code, TransportLine line, Depot depot) {
        TrainModel model = mock(TrainModel.class);
        when(model.getSeries()).thenReturn("9000");
        Train train = mock(Train.class);
        when(train.getId()).thenReturn(id);
        when(train.getCode()).thenReturn(code);
        when(train.getModel()).thenReturn(model);
        when(train.getFleetRole()).thenReturn(FleetRole.REGULAR_SERVICE);
        when(train.getAssignedLine()).thenReturn(line);
        when(train.getHomeDepot()).thenReturn(depot);
        lenient().when(train.isActive()).thenReturn(true);
        return train;
    }

    private long inServiceTrainCount(RailwaySimulationState state) {
        return state.trains().stream()
                .filter(train -> train.status() == TrainStatus.IN_SERVICE)
                .count();
    }

    private ZonedDateTime at(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), SERVICE_ZONE);
    }
}
