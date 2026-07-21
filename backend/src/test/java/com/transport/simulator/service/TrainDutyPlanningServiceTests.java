package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Depot;
import com.transport.simulator.entity.LineServiceLevel;
import com.transport.simulator.entity.ServicePeriod;
import com.transport.simulator.entity.Train;
import com.transport.simulator.entity.TrainModel;
import com.transport.simulator.enums.DepotMovementType;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.OperatingDayType;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.ServicePeriodType;
import com.transport.simulator.enums.TrainPositionState;
import com.transport.simulator.repository.LineServiceLevelRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.service.model.LineDepotConfiguration;
import com.transport.simulator.service.model.LineServiceOperationState;
import com.transport.simulator.service.model.ResolvedLineServiceConfiguration;
import com.transport.simulator.service.model.RouteStopConfiguration;
import com.transport.simulator.service.model.ServiceOperationState;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
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
class TrainDutyPlanningServiceTests {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Europe/Madrid");
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 7, 21);
    private static final String CALENDAR_CODE = "WEEKDAY_STANDARD";

    @Mock
    private ServiceOperationStateService operationStateService;

    @Mock
    private LineServiceLevelRepository serviceLevelRepository;

    @Mock
    private TrainRepository trainRepository;

    private List<RouteStopConfiguration> route;

    @BeforeEach
    void setUp() {
        route = List.of(
                new RouteStopConfiguration(101L, "STA", "Estación A", 1, 60, 10),
                new RouteStopConfiguration(102L, "STB", "Estación B", 2, 120, 20),
                new RouteStopConfiguration(103L, "STC", "Estación C", 3, null, 10)
        );
    }

    @Test
    void shouldCalculateFleetTargetsAndStaggerAdditionalPeakDuties() {
        ZonedDateTime evaluatedAt = at(SERVICE_DATE, 8, 0, 0);
        ResolvedLineServiceConfiguration configuration = configuration(
                SERVICE_DATE,
                LocalTime.of(6, 0),
                LocalTime.of(10, 0),
                "PEAK",
                ServicePeriodType.PEAK
        );
        stubOperation(evaluatedAt, configuration);
        stubLevels(List.of(
                level("OFF_PEAK", ServicePeriodType.OFF_PEAK, LocalTime.of(6, 0), LocalTime.of(7, 0), 220),
                level("PEAK", ServicePeriodType.PEAK, LocalTime.of(7, 0), LocalTime.of(10, 0), 110)
        ));
        stubTrains(List.of(
                train(1L, "T-A-1", 201L, "DEP-A", 101L),
                train(2L, "T-C-1", 202L, "DEP-C", 103L),
                train(3L, "T-A-2", 201L, "DEP-A", 101L),
                train(4L, "T-C-2", 202L, "DEP-C", 103L)
        ));

        var linePlan = planner(evaluatedAt).getCurrentPlan().lines().getFirst();

        assertThat(linePlan.roundTripSeconds()).isEqualTo(440);
        assertThat(linePlan.periods()).extracting("targetFleetSize").containsExactly(2, 4);
        assertThat(linePlan.periods()).extracting("headwaySeconds").containsExactly(220, 110);
        assertThat(linePlan.duties()).hasSize(4);
        assertThat(linePlan.duties().get(2).plannedStartAt()).isEqualTo(at(SERVICE_DATE, 7, 0, 0));
        assertThat(linePlan.duties().get(3).plannedStartAt()).isEqualTo(at(SERVICE_DATE, 7, 0, 55));
        assertThat(linePlan.duties()).extracting("initialDirection")
                .containsExactly(
                        ServiceDirection.OUTBOUND,
                        ServiceDirection.INBOUND,
                        ServiceDirection.OUTBOUND,
                        ServiceDirection.INBOUND
                );
    }

    @Test
    void shouldCalculateStationStopsSegmentsProgressAndDirectionChanges() {
        ZonedDateTime evaluatedAt = at(SERVICE_DATE, 6, 0, 30);
        ResolvedLineServiceConfiguration configuration = configuration(
                SERVICE_DATE,
                LocalTime.of(6, 0),
                LocalTime.of(10, 0),
                "REGULAR",
                ServicePeriodType.REGULAR
        );
        stubOperation(evaluatedAt, configuration);
        stubLevels(List.of(
                level("REGULAR", ServicePeriodType.REGULAR, LocalTime.of(6, 0), LocalTime.of(10, 0), 220)
        ));
        stubTrains(List.of(
                train(1L, "T-A-1", 201L, "DEP-A", 101L),
                train(2L, "T-C-1", 202L, "DEP-C", 103L)
        ));

        var linePlan = planner(evaluatedAt).getCurrentPlan().lines().getFirst();
        var movingPosition = linePlan.positions().getFirst();

        assertThat(movingPosition.state()).isEqualTo(TrainPositionState.BETWEEN_STATIONS);
        assertThat(movingPosition.direction()).isEqualTo(ServiceDirection.OUTBOUND);
        assertThat(movingPosition.previousStationCode()).isEqualTo("STA");
        assertThat(movingPosition.nextStationCode()).isEqualTo("STB");
        assertThat(movingPosition.progressPercentage()).isEqualTo(16);
        assertThat(movingPosition.secondsUntilNextStation()).isEqualTo(50);
        assertThat(movingPosition.estimatedArrivalAt()).isEqualTo(at(SERVICE_DATE, 6, 1, 20));

        ZonedDateTime atMiddleStation = at(SERVICE_DATE, 6, 1, 20);
        stubOperation(atMiddleStation, configuration);
        var stoppedPosition = planner(atMiddleStation).getCurrentPlan().lines().getFirst().positions().getFirst();
        assertThat(stoppedPosition.state()).isEqualTo(TrainPositionState.AT_STATION);
        assertThat(stoppedPosition.currentStationCode()).isEqualTo("STB");
        assertThat(stoppedPosition.nextStationCode()).isEqualTo("STC");
        assertThat(stoppedPosition.secondsUntilNextStation()).isEqualTo(140);

        ZonedDateTime atTerminal = at(SERVICE_DATE, 6, 3, 40);
        stubOperation(atTerminal, configuration);
        var terminalPosition = planner(atTerminal).getCurrentPlan().lines().getFirst().positions().getFirst();
        assertThat(terminalPosition.state()).isEqualTo(TrainPositionState.AT_STATION);
        assertThat(terminalPosition.currentStationCode()).isEqualTo("STC");
        assertThat(terminalPosition.nextStationCode()).isEqualTo("STB");
        assertThat(terminalPosition.direction()).isEqualTo(ServiceDirection.INBOUND);
    }

    @Test
    void shouldKeepTheRequestedWithdrawalAndEnterTheDepotAfterReturningToTheTerminal() {
        ZonedDateTime evaluatedAt = at(SERVICE_DATE, 6, 30, 0);
        ResolvedLineServiceConfiguration configuration = configuration(
                SERVICE_DATE,
                LocalTime.of(6, 0),
                LocalTime.of(10, 0),
                "QUIET",
                ServicePeriodType.OFF_PEAK
        );
        stubOperation(evaluatedAt, configuration);
        stubLevels(List.of(
                level("REGULAR", ServicePeriodType.REGULAR, LocalTime.of(6, 0), LocalTime.of(7, 0), 220),
                level("QUIET", ServicePeriodType.OFF_PEAK, LocalTime.of(7, 0), LocalTime.of(10, 0), 440)
        ));
        stubTrains(List.of(
                train(1L, "T-A-1", 201L, "DEP-A", 101L),
                train(2L, "T-C-1", 202L, "DEP-C", 103L)
        ));

        var linePlan = planner(evaluatedAt).getCurrentPlan().lines().getFirst();
        var withdrawnDuty = linePlan.duties().get(1);

        assertThat(withdrawnDuty.requestedReleaseAt()).isEqualTo(at(SERVICE_DATE, 7, 0, 0));
        assertThat(withdrawnDuty.plannedReleaseAt()).isEqualTo(at(SERVICE_DATE, 7, 0, 30));
        assertThat(linePlan.depotMovements())
                .filteredOn(movement -> movement.dutyNumber() == withdrawnDuty.dutyNumber())
                .extracting("movementType", "scheduledAt")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                DepotMovementType.EXIT,
                                withdrawnDuty.plannedStartAt()
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                DepotMovementType.ENTRY,
                                withdrawnDuty.plannedReleaseAt()
                        )
                );
    }

    @Test
    void shouldBuildTheCorrectOperatingWindowAcrossMidnight() {
        LocalDate nextDay = SERVICE_DATE.plusDays(1);
        ZonedDateTime evaluatedAt = at(nextDay, 0, 30, 0);
        ResolvedLineServiceConfiguration configuration = configuration(
                SERVICE_DATE,
                LocalTime.of(23, 0),
                LocalTime.of(2, 0),
                "NIGHT",
                ServicePeriodType.REGULAR
        );
        stubOperation(evaluatedAt, configuration);
        stubLevels(List.of(
                level("NIGHT", ServicePeriodType.REGULAR, LocalTime.of(23, 0), LocalTime.of(2, 0), 220)
        ));
        stubTrains(List.of(
                train(1L, "T-A-1", 201L, "DEP-A", 101L),
                train(2L, "T-C-1", 202L, "DEP-C", 103L)
        ));

        var linePlan = planner(evaluatedAt).getCurrentPlan().lines().getFirst();

        assertThat(linePlan.serviceDate()).isEqualTo(SERVICE_DATE);
        assertThat(linePlan.serviceStartsAt()).isEqualTo(at(SERVICE_DATE, 23, 0, 0));
        assertThat(linePlan.serviceEndsAt()).isEqualTo(at(nextDay, 2, 0, 0));
        assertThat(linePlan.periods()).singleElement().satisfies(period -> {
            assertThat(period.startsAt()).isEqualTo(linePlan.serviceStartsAt());
            assertThat(period.endsAt()).isEqualTo(linePlan.serviceEndsAt());
        });
    }

    @Test
    void shouldFailInsteadOfUsingNonEligibleFleetWhenAServiceOriginHasNoTrain() {
        ZonedDateTime evaluatedAt = at(SERVICE_DATE, 6, 30, 0);
        ResolvedLineServiceConfiguration configuration = configuration(
                SERVICE_DATE,
                LocalTime.of(6, 0),
                LocalTime.of(10, 0),
                "REGULAR",
                ServicePeriodType.REGULAR
        );
        stubOperation(evaluatedAt, configuration);
        stubLevels(List.of(
                level("REGULAR", ServicePeriodType.REGULAR, LocalTime.of(6, 0), LocalTime.of(10, 0), 220)
        ));
        stubTrains(List.of(train(1L, "T-A-1", 201L, "DEP-A", 101L)));

        assertThatThrownBy(() -> planner(evaluatedAt).getCurrentPlan())
                .isInstanceOf(ServiceConfigurationException.class)
                .hasMessageContaining("Insufficient active 9000 series")
                .hasMessageContaining("STC");
    }

    @Test
    void shouldDispatchTrainsFromIntermediateDepotsToTheirNearestTerminals() {
        route = List.of(
                new RouteStopConfiguration(101L, "STA", "Terminal A", 1, 60, 10),
                new RouteStopConfiguration(102L, "STB", "Cochera A", 2, 60, 10),
                new RouteStopConfiguration(103L, "STC", "Centro", 3, 60, 10),
                new RouteStopConfiguration(104L, "STD", "Cochera E", 4, 60, 10),
                new RouteStopConfiguration(105L, "STE", "Terminal E", 5, null, 10)
        );
        ZonedDateTime evaluatedAt = at(SERVICE_DATE, 6, 30, 0);
        ResolvedLineServiceConfiguration configuration = configuration(
                SERVICE_DATE,
                LocalTime.of(6, 0),
                LocalTime.of(10, 0),
                "REGULAR",
                ServicePeriodType.REGULAR,
                List.of(
                        new LineDepotConfiguration(201L, "DEP-B", "Cochera B", 102L, "STB", 101L, "STA", 1, true, true),
                        new LineDepotConfiguration(202L, "DEP-D", "Cochera D", 104L, "STD", 105L, "STE", 2, true, true)
                )
        );
        stubOperation(evaluatedAt, configuration);
        stubLevels(List.of(
                level("REGULAR", ServicePeriodType.REGULAR, LocalTime.of(6, 0), LocalTime.of(10, 0), 300)
        ));
        stubTrains(List.of(
                train(1L, "T-B-1", 201L, "DEP-B", 102L),
                train(2L, "T-D-1", 202L, "DEP-D", 104L)
        ));

        var duties = planner(evaluatedAt).getCurrentPlan().lines().getFirst().duties();

        assertThat(duties).hasSize(2);
        assertThat(duties.get(0).originStationCode()).isEqualTo("STA");
        assertThat(duties.get(0).homeDepotCode()).isEqualTo("DEP-B");
        assertThat(duties.get(1).originStationCode()).isEqualTo("STE");
        assertThat(duties.get(1).homeDepotCode()).isEqualTo("DEP-D");
    }

    private TrainDutyPlanningService planner(ZonedDateTime evaluatedAt) {
        return new TrainDutyPlanningService(
                Clock.fixed(evaluatedAt.toInstant(), SERVICE_ZONE),
                operationStateService,
                serviceLevelRepository,
                trainRepository
        );
    }

    private void stubOperation(
            ZonedDateTime evaluatedAt,
            ResolvedLineServiceConfiguration configuration
    ) {
        LineServiceOperationState lineState = new LineServiceOperationState(
                1L,
                "L1",
                ServiceOperationPhase.OPERATING,
                Optional.of(configuration),
                1,
                1
        );
        when(operationStateService.getStateAt(evaluatedAt)).thenReturn(new ServiceOperationState(
                evaluatedAt,
                ServiceOperationPhase.OPERATING,
                1,
                List.of(lineState)
        ));
    }

    private void stubLevels(List<LineServiceLevel> levels) {
        when(serviceLevelRepository
                .findAllByLineIdAndServicePeriodServiceCalendarCodeAndActiveTrueAndServicePeriodActiveTrueOrderByServicePeriodPeriodOrderAsc(
                        1L,
                        CALENDAR_CODE
                ))
                .thenReturn(levels);
    }

    private void stubTrains(List<Train> trains) {
        when(trainRepository
                .findAllByAssignedLineIdAndFleetRoleAndModelSeriesAndActiveTrueAndModelActiveTrueAndHomeDepotActiveTrueOrderByDispatchOrderAscCodeAsc(
                        1L,
                        FleetRole.REGULAR_SERVICE,
                        "9000"
                ))
                .thenReturn(trains);
    }

    private ResolvedLineServiceConfiguration configuration(
            LocalDate serviceDate,
            LocalTime serviceStart,
            LocalTime serviceEnd,
            String periodCode,
            ServicePeriodType periodType
    ) {
        return configuration(
                serviceDate,
                serviceStart,
                serviceEnd,
                periodCode,
                periodType,
                List.of(
                        new LineDepotConfiguration(201L, "DEP-A", "Cochera A", 101L, "STA", 101L, "STA", 1, true, true),
                        new LineDepotConfiguration(202L, "DEP-C", "Cochera C", 103L, "STC", 103L, "STC", 2, true, true)
                )
        );
    }

    private ResolvedLineServiceConfiguration configuration(
            LocalDate serviceDate,
            LocalTime serviceStart,
            LocalTime serviceEnd,
            String periodCode,
            ServicePeriodType periodType,
            List<LineDepotConfiguration> depots
    ) {
        return new ResolvedLineServiceConfiguration(
                1L,
                "L1",
                serviceDate,
                CALENDAR_CODE,
                OperatingDayType.WEEKDAY,
                serviceStart,
                serviceEnd,
                periodCode,
                periodType,
                serviceStart,
                serviceEnd,
                220,
                route,
                depots
        );
    }

    private LineServiceLevel level(
            String code,
            ServicePeriodType type,
            LocalTime startsAt,
            LocalTime endsAt,
            int headwaySeconds
    ) {
        ServicePeriod period = mock(ServicePeriod.class);
        when(period.getCode()).thenReturn(code);
        when(period.getPeriodType()).thenReturn(type);
        when(period.getStartTime()).thenReturn(startsAt);
        when(period.getEndTime()).thenReturn(endsAt);
        LineServiceLevel level = mock(LineServiceLevel.class);
        when(level.getServicePeriod()).thenReturn(period);
        when(level.getHeadwaySeconds()).thenReturn(headwaySeconds);
        return level;
    }

    private Train train(
            Long trainId,
            String trainCode,
            Long depotId,
            String depotCode,
            Long stationId
    ) {
        Depot depot = mock(Depot.class);
        when(depot.getId()).thenReturn(depotId);
        when(depot.getCode()).thenReturn(depotCode);
        TrainModel model = mock(TrainModel.class);
        when(model.getSeries()).thenReturn("9000");
        Train train = mock(Train.class);
        when(train.getId()).thenReturn(trainId);
        when(train.getCode()).thenReturn(trainCode);
        when(train.getHomeDepot()).thenReturn(depot);
        when(train.getModel()).thenReturn(model);
        return train;
    }

    private ZonedDateTime at(LocalDate date, int hour, int minute, int second) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute, second), SERVICE_ZONE);
    }
}
