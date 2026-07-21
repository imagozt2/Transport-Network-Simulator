package com.transport.simulator.service;

import com.transport.simulator.entity.LineServiceLevel;
import com.transport.simulator.entity.ServicePeriod;
import com.transport.simulator.entity.Train;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.DepotMovementType;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.TrainPositionState;
import com.transport.simulator.repository.LineServiceLevelRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.service.model.LineDutyPlan;
import com.transport.simulator.service.model.LineServiceOperationState;
import com.transport.simulator.service.model.PlannedTrainDuty;
import com.transport.simulator.service.model.PlannedDepotMovement;
import com.transport.simulator.service.model.LineDepotConfiguration;
import com.transport.simulator.service.model.ResolvedLineServiceConfiguration;
import com.transport.simulator.service.model.RouteStopConfiguration;
import com.transport.simulator.service.model.ServiceDutyPlan;
import com.transport.simulator.service.model.ServiceOperationState;
import com.transport.simulator.service.model.ServicePeriodFleetPlan;
import com.transport.simulator.service.model.SimulatedTrainPosition;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TrainDutyPlanningService {

    private static final String REGULAR_SERVICE_SERIES = "9000";

    private final Clock serviceClock;
    private final ServiceOperationStateService serviceOperationStateService;
    private final LineServiceLevelRepository lineServiceLevelRepository;
    private final TrainRepository trainRepository;

    public TrainDutyPlanningService(
            Clock serviceClock,
            ServiceOperationStateService serviceOperationStateService,
            LineServiceLevelRepository lineServiceLevelRepository,
            TrainRepository trainRepository
    ) {
        this.serviceClock = serviceClock;
        this.serviceOperationStateService = serviceOperationStateService;
        this.lineServiceLevelRepository = lineServiceLevelRepository;
        this.trainRepository = trainRepository;
    }

    public ServiceDutyPlan getCurrentPlan() {
        return getPlanAt(ZonedDateTime.now(serviceClock));
    }

    public ServiceDutyPlan getPlanAt(ZonedDateTime requestedDateTime) {
        ZonedDateTime evaluatedAt = requestedDateTime.withZoneSameInstant(serviceClock.getZone());
        ServiceOperationState operationState = serviceOperationStateService.getStateAt(evaluatedAt);
        return getPlan(operationState);
    }

    public ServiceDutyPlan getPlan(ServiceOperationState operationState) {
        ZonedDateTime evaluatedAt = operationState.evaluatedAt();
        List<LineDutyPlan> linePlans = operationState.lines()
                .stream()
                .filter(LineServiceOperationState::serviceOpen)
                .map(lineState -> createLinePlan(lineState, evaluatedAt))
                .toList();
        return new ServiceDutyPlan(evaluatedAt, linePlans);
    }

    private LineDutyPlan createLinePlan(LineServiceOperationState lineState, ZonedDateTime evaluatedAt) {
        ResolvedLineServiceConfiguration configuration = lineState.configuration().orElseThrow();
        ZonedDateTime serviceStartsAt = atServiceTime(
                configuration.serviceDate(),
                configuration.serviceStartTime()
        );
        LocalDate serviceEndDate = crossesMidnight(
                configuration.serviceStartTime(),
                configuration.serviceEndTime()
        )
                ? configuration.serviceDate().plusDays(1)
                : configuration.serviceDate();
        ZonedDateTime serviceEndsAt = atServiceTime(serviceEndDate, configuration.serviceEndTime());
        long roundTripSeconds = calculateRoundTripSeconds(configuration.route());
        List<ServicePeriodFleetPlan> periodPlans = resolvePeriodPlans(
                configuration,
                serviceStartsAt,
                serviceEndsAt,
                roundTripSeconds
        );
        List<PlannedTrainDuty> duties = assignRegularServiceTrains(
                lineState.lineId(),
                lineState.lineCode(),
                configuration.depots(),
                configuration.route(),
                generateDuties(configuration.route(), periodPlans, serviceEndsAt, roundTripSeconds),
                roundTripSeconds
        );
        List<SimulatedTrainPosition> positions = calculatePositions(
                lineState.lineId(),
                lineState.lineCode(),
                configuration.route(),
                duties,
                evaluatedAt,
                roundTripSeconds
        );
        List<PlannedDepotMovement> depotMovements = buildDepotMovements(
                lineState.lineId(),
                lineState.lineCode(),
                duties
        );

        return new LineDutyPlan(
                lineState.lineId(),
                lineState.lineCode(),
                configuration.serviceDate(),
                serviceStartsAt,
                serviceEndsAt,
                roundTripSeconds,
                periodPlans,
                duties,
                positions,
                depotMovements
        );
    }

    private List<PlannedDepotMovement> buildDepotMovements(
            Long lineId,
            String lineCode,
            List<PlannedTrainDuty> duties
    ) {
        return duties.stream()
                .flatMap(duty -> List.of(
                        toDepotMovement(lineId, lineCode, duty, DepotMovementType.EXIT, duty.plannedStartAt()),
                        toDepotMovement(lineId, lineCode, duty, DepotMovementType.ENTRY, duty.plannedReleaseAt())
                ).stream())
                .sorted(Comparator.comparing(PlannedDepotMovement::scheduledAt)
                        .thenComparingInt(PlannedDepotMovement::dutyNumber)
                        .thenComparing(PlannedDepotMovement::movementType))
                .toList();
    }

    private PlannedDepotMovement toDepotMovement(
            Long lineId,
            String lineCode,
            PlannedTrainDuty duty,
            DepotMovementType movementType,
            ZonedDateTime scheduledAt
    ) {
        return new PlannedDepotMovement(
                duty.dutyNumber(),
                duty.trainId(),
                duty.trainCode(),
                lineId,
                lineCode,
                duty.homeDepotId(),
                duty.homeDepotCode(),
                duty.originStationId(),
                duty.originStationCode(),
                movementType,
                scheduledAt
        );
    }

    private List<SimulatedTrainPosition> calculatePositions(
            Long lineId,
            String lineCode,
            List<RouteStopConfiguration> route,
            List<PlannedTrainDuty> duties,
            ZonedDateTime evaluatedAt,
            long roundTripSeconds
    ) {
        if (route.size() < 2) {
            throw new ServiceConfigurationException("Line " + lineCode + " requires at least two route stops");
        }

        return duties.stream()
                .filter(duty -> duty.isActiveAt(evaluatedAt))
                .map(duty -> calculatePosition(
                        lineId,
                        lineCode,
                        route,
                        duty,
                        evaluatedAt,
                        roundTripSeconds
                ))
                .toList();
    }

    private SimulatedTrainPosition calculatePosition(
            Long lineId,
            String lineCode,
            List<RouteStopConfiguration> route,
            PlannedTrainDuty duty,
            ZonedDateTime evaluatedAt,
            long roundTripSeconds
    ) {
        List<MovementSegment> cycle = buildMovementCycle(route, duty.initialDirection());
        long calculatedCycleSeconds = cycle.stream().mapToLong(MovementSegment::durationSeconds).sum();
        if (calculatedCycleSeconds != roundTripSeconds) {
            throw new ServiceConfigurationException(
                    "Movement cycle duration does not match the round trip for line " + lineCode
            );
        }

        long elapsedDutySeconds = Duration.between(duty.plannedStartAt(), evaluatedAt).toSeconds();
        long cycleOffsetSeconds = Math.floorMod(elapsedDutySeconds, roundTripSeconds);
        for (MovementSegment segment : cycle) {
            if (cycleOffsetSeconds < segment.durationSeconds()) {
                return positionWithinSegment(lineId, lineCode, duty, segment, cycleOffsetSeconds, evaluatedAt);
            }
            cycleOffsetSeconds -= segment.durationSeconds();
        }
        throw new ServiceConfigurationException("Unable to resolve train position for line " + lineCode);
    }

    private List<MovementSegment> buildMovementCycle(
            List<RouteStopConfiguration> route,
            ServiceDirection initialDirection
    ) {
        List<MovementSegment> cycle = new ArrayList<>();
        if (initialDirection == ServiceDirection.OUTBOUND) {
            addOutboundSegments(route, cycle);
            addInboundSegments(route, cycle);
        } else {
            addInboundSegments(route, cycle);
            addOutboundSegments(route, cycle);
        }
        return cycle;
    }

    private void addOutboundSegments(List<RouteStopConfiguration> route, List<MovementSegment> cycle) {
        for (int index = 0; index < route.size() - 1; index++) {
            RouteStopConfiguration current = route.get(index);
            cycle.add(new MovementSegment(
                    current,
                    route.get(index + 1),
                    ServiceDirection.OUTBOUND,
                    current.dwellSeconds(),
                    requiredTravelSeconds(current)
            ));
        }
    }

    private void addInboundSegments(List<RouteStopConfiguration> route, List<MovementSegment> cycle) {
        for (int index = route.size() - 1; index > 0; index--) {
            RouteStopConfiguration current = route.get(index);
            cycle.add(new MovementSegment(
                    current,
                    route.get(index - 1),
                    ServiceDirection.INBOUND,
                    current.dwellSeconds(),
                    requiredTravelSeconds(route.get(index - 1))
            ));
        }
    }

    private long requiredTravelSeconds(RouteStopConfiguration stop) {
        if (stop.travelSecondsToNext() == null || stop.travelSecondsToNext() <= 0) {
            throw new ServiceConfigurationException(
                    "Missing travel time after station " + stop.stationCode()
            );
        }
        return stop.travelSecondsToNext();
    }

    private SimulatedTrainPosition positionWithinSegment(
            Long lineId,
            String lineCode,
            PlannedTrainDuty duty,
            MovementSegment segment,
            long segmentOffsetSeconds,
            ZonedDateTime evaluatedAt
    ) {
        boolean atStation = segmentOffsetSeconds < segment.dwellSeconds();
        long travelElapsedSeconds = atStation ? 0 : segmentOffsetSeconds - segment.dwellSeconds();
        long secondsUntilArrival = atStation
                ? segment.dwellSeconds() - segmentOffsetSeconds + segment.travelSeconds()
                : segment.travelSeconds() - travelElapsedSeconds;
        int progressPercentage = atStation
                ? 0
                : (int) Math.min(99, travelElapsedSeconds * 100 / segment.travelSeconds());

        return new SimulatedTrainPosition(
                duty.dutyNumber(),
                duty.trainId(),
                duty.trainCode(),
                lineId,
                lineCode,
                atStation ? TrainPositionState.AT_STATION : TrainPositionState.BETWEEN_STATIONS,
                segment.direction(),
                atStation ? segment.previous().stationId() : null,
                atStation ? segment.previous().stationCode() : null,
                segment.previous().stationId(),
                segment.previous().stationCode(),
                segment.next().stationId(),
                segment.next().stationCode(),
                progressPercentage,
                secondsUntilArrival,
                evaluatedAt.plusSeconds(secondsUntilArrival),
                evaluatedAt
        );
    }

    private List<ServicePeriodFleetPlan> resolvePeriodPlans(
            ResolvedLineServiceConfiguration configuration,
            ZonedDateTime serviceStartsAt,
            ZonedDateTime serviceEndsAt,
            long roundTripSeconds
    ) {
        List<LineServiceLevel> levels = lineServiceLevelRepository
                .findAllByLineIdAndServicePeriodServiceCalendarCodeAndActiveTrueAndServicePeriodActiveTrueOrderByServicePeriodPeriodOrderAsc(
                        configuration.lineId(),
                        configuration.calendarCode()
                );
        if (levels.isEmpty()) {
            throw new ServiceConfigurationException(
                    "No service levels found for line " + configuration.lineCode()
                            + " and calendar " + configuration.calendarCode()
            );
        }

        List<ServicePeriodFleetPlan> plans = levels.stream()
                .map(level -> toPeriodPlan(
                        level,
                        configuration.serviceDate(),
                        configuration.serviceStartTime(),
                        serviceStartsAt,
                        serviceEndsAt,
                        roundTripSeconds
                ))
                .toList();
        if (plans.isEmpty()) {
            throw new ServiceConfigurationException(
                    "No service periods overlap the operating window for line " + configuration.lineCode()
            );
        }
        return plans;
    }

    private ServicePeriodFleetPlan toPeriodPlan(
            LineServiceLevel level,
            LocalDate serviceDate,
            LocalTime serviceStartTime,
            ZonedDateTime serviceStartsAt,
            ZonedDateTime serviceEndsAt,
            long roundTripSeconds
    ) {
        ServicePeriod period = level.getServicePeriod();
        LocalDate periodStartDate = period.getStartTime().isBefore(serviceStartTime)
                ? serviceDate.plusDays(1)
                : serviceDate;
        ZonedDateTime periodStartsAt = atServiceTime(periodStartDate, period.getStartTime());
        LocalDate periodEndDate = period.getEndTime().isAfter(period.getStartTime())
                ? periodStartDate
                : periodStartDate.plusDays(1);
        ZonedDateTime periodEndsAt = atServiceTime(periodEndDate, period.getEndTime());

        ZonedDateTime effectiveStart = periodStartsAt.isBefore(serviceStartsAt)
                ? serviceStartsAt
                : periodStartsAt;
        ZonedDateTime effectiveEnd = periodEndsAt.isAfter(serviceEndsAt)
                ? serviceEndsAt
                : periodEndsAt;
        if (!effectiveEnd.isAfter(effectiveStart)) {
            throw new ServiceConfigurationException(
                    "Period " + period.getCode() + " does not overlap the configured service window"
            );
        }

        return new ServicePeriodFleetPlan(
                period.getCode(),
                period.getPeriodType(),
                effectiveStart,
                effectiveEnd,
                level.getHeadwaySeconds(),
                requiredFleetSize(roundTripSeconds, level.getHeadwaySeconds())
        );
    }

    private List<UnassignedDuty> generateDuties(
            List<RouteStopConfiguration> route,
            List<ServicePeriodFleetPlan> periods,
            ZonedDateTime serviceEndsAt,
            long roundTripSeconds
    ) {
        List<MutableDuty> duties = new ArrayList<>();
        int nextDutyNumber = 1;

        for (ServicePeriodFleetPlan period : periods) {
            List<MutableDuty> activeAtPeriodStart = duties.stream()
                    .filter(duty -> duty.isActiveAt(period.startsAt()))
                    .toList();
            int fleetDifference = period.targetFleetSize() - activeAtPeriodStart.size();

            if (fleetDifference > 0) {
                long operatingSpacingSeconds = Math.max(
                        1,
                        roundTripSeconds / period.targetFleetSize()
                );
                ZonedDateTime nextDeparture = activeAtPeriodStart.isEmpty()
                        ? period.startsAt()
                        : period.startsAt().plusSeconds(Math.max(1, operatingSpacingSeconds / 2L));
                int remainingAdditions = fleetDifference;
                while (remainingAdditions > 0 && nextDeparture.isBefore(period.endsAt())) {
                    for (ServiceDirection direction : ServiceDirection.values()) {
                        if (remainingAdditions == 0) {
                            break;
                        }
                        RouteStopConfiguration origin = direction == ServiceDirection.OUTBOUND
                                ? route.getFirst()
                                : route.getLast();
                        duties.add(new MutableDuty(
                                nextDutyNumber,
                                direction,
                                origin.stationId(),
                                origin.stationCode(),
                                period.periodCode(),
                                period.headwaySeconds(),
                                nextDeparture
                        ));
                        nextDutyNumber++;
                        remainingAdditions--;
                    }
                    nextDeparture = nextDeparture.plusSeconds(operatingSpacingSeconds);
                }
            } else if (fleetDifference < 0) {
                activeAtPeriodStart.stream()
                        .sorted(Comparator.comparingInt(MutableDuty::dutyNumber).reversed())
                        .limit(Math.abs((long) fleetDifference))
                        .forEach(duty -> duty.releaseAt(period.startsAt()));
            }
        }

        duties.stream()
                .filter(duty -> duty.plannedReleaseAt() == null)
                .forEach(duty -> duty.releaseAt(serviceEndsAt));

        return duties.stream().map(MutableDuty::toUnassignedDuty).toList();
    }

    private List<PlannedTrainDuty> assignRegularServiceTrains(
            Long lineId,
            String lineCode,
            List<LineDepotConfiguration> depots,
            List<RouteStopConfiguration> route,
            List<UnassignedDuty> duties,
            long roundTripSeconds
    ) {
        List<Long> operationalDepotIds = depots.stream()
                .filter(LineDepotConfiguration::dispatchEnabled)
                .filter(LineDepotConfiguration::receptionEnabled)
                .map(LineDepotConfiguration::depotId)
                .toList();
        if (operationalDepotIds.isEmpty()) {
            throw new ServiceConfigurationException(
                    "Line " + lineCode + " has no depot enabled for both dispatch and reception"
            );
        }
        validateOperationalDepotsBelongToRoute(lineCode, depots, operationalDepotIds, route);
        List<Train> eligibleTrains = trainRepository
                .findAllByAssignedLineIdAndFleetRoleAndModelSeriesAndActiveTrueAndModelActiveTrueAndHomeDepotActiveTrueOrderByDispatchOrderAscCodeAsc(
                        lineId,
                        FleetRole.REGULAR_SERVICE,
                        REGULAR_SERVICE_SERIES
                ).stream()
                .filter(train -> operationalDepotIds.contains(train.getHomeDepot().getId()))
                .toList();
        Map<Long, ZonedDateTime> availableFrom = new HashMap<>();

        return duties.stream()
                .sorted(Comparator.comparing(UnassignedDuty::plannedStartAt)
                        .thenComparingInt(UnassignedDuty::dutyNumber))
                .map(duty -> assignTrain(
                        lineCode,
                        duty,
                        eligibleTrains,
                        depots,
                        route,
                        availableFrom,
                        roundTripSeconds
                ))
                .sorted(Comparator.comparingInt(PlannedTrainDuty::dutyNumber))
                .toList();
    }

    private PlannedTrainDuty assignTrain(
            String lineCode,
            UnassignedDuty duty,
            List<Train> eligibleTrains,
            List<LineDepotConfiguration> depots,
            List<RouteStopConfiguration> route,
            Map<Long, ZonedDateTime> availableFrom,
            long roundTripSeconds
    ) {
        Train train = eligibleTrains.stream()
                .filter(candidate -> depotServesTerminal(
                        candidate.getHomeDepot().getId(), duty.originStationId(), depots
                ))
                .filter(candidate -> {
                    ZonedDateTime nextAvailableAt = availableFrom.get(candidate.getId());
                    return nextAvailableAt == null || !nextAvailableAt.isAfter(duty.plannedStartAt());
                })
                .findFirst()
                .orElseThrow(() -> new ServiceConfigurationException(
                        "Insufficient active 9000 series regular-service fleet for line " + lineCode
                                + " at origin " + duty.originStationCode()
                                + " and departure " + duty.plannedStartAt()
                ));
        ZonedDateTime depotEntryAt = nextReturnToHomeDepot(
                duty.plannedStartAt(),
                duty.plannedReleaseAt(),
                roundTripSeconds
        );
        availableFrom.put(train.getId(), depotEntryAt);

        return new PlannedTrainDuty(
                duty.dutyNumber(),
                train.getId(),
                train.getCode(),
                train.getModel().getSeries(),
                train.getHomeDepot().getId(),
                train.getHomeDepot().getCode(),
                duty.initialDirection(),
                duty.originStationId(),
                duty.originStationCode(),
                duty.startingPeriodCode(),
                duty.startingHeadwaySeconds(),
                duty.plannedStartAt(),
                duty.plannedReleaseAt(),
                depotEntryAt
        );
    }

    private void validateOperationalDepotsBelongToRoute(
            String lineCode,
            List<LineDepotConfiguration> depots,
            List<Long> operationalDepotIds,
            List<RouteStopConfiguration> route
    ) {
        List<Long> routeStationIds = route.stream().map(RouteStopConfiguration::stationId).toList();
        depots.stream()
                .filter(depot -> operationalDepotIds.contains(depot.depotId()))
                .filter(depot -> !routeStationIds.contains(depot.stationId())
                        || !routeStationIds.contains(depot.dispatchTerminalStationId()))
                .findFirst()
                .ifPresent(depot -> {
                    throw new ServiceConfigurationException(
                            "Depot " + depot.depotCode() + " or its dispatch terminal does not belong to route "
                                    + lineCode
                    );
                });
        Long firstTerminalId = route.getFirst().stationId();
        Long lastTerminalId = route.getLast().stationId();
        depots.stream()
                .filter(depot -> operationalDepotIds.contains(depot.depotId()))
                .filter(depot -> !depot.dispatchTerminalStationId().equals(firstTerminalId)
                        && !depot.dispatchTerminalStationId().equals(lastTerminalId))
                .findFirst()
                .ifPresent(depot -> {
                    throw new ServiceConfigurationException(
                            "Dispatch station " + depot.dispatchTerminalStationCode()
                                    + " is not a terminal of line " + lineCode
                    );
                });
    }

    private boolean depotServesTerminal(
            Long depotId,
            Long terminalStationId,
            List<LineDepotConfiguration> depots
    ) {
        LineDepotConfiguration depot = depots.stream()
                .filter(candidate -> candidate.depotId().equals(depotId))
                .findFirst()
                .orElseThrow(() -> new ServiceConfigurationException(
                        "Train depot is not enabled for this line: " + depotId
                ));
        return depot.dispatchTerminalStationId().equals(terminalStationId);
    }

    private ZonedDateTime nextReturnToHomeDepot(
            ZonedDateTime dutyStartsAt,
            ZonedDateTime requestedReleaseAt,
            long roundTripSeconds
    ) {
        long requestedDutySeconds = Duration.between(dutyStartsAt, requestedReleaseAt).toSeconds();
        long completedCycles = Math.floorDiv(requestedDutySeconds, roundTripSeconds);
        long entryOffsetSeconds = Math.multiplyExact(completedCycles, roundTripSeconds);
        if (entryOffsetSeconds < requestedDutySeconds) {
            entryOffsetSeconds = Math.addExact(entryOffsetSeconds, roundTripSeconds);
        }
        return dutyStartsAt.plusSeconds(entryOffsetSeconds);
    }

    private long calculateRoundTripSeconds(List<RouteStopConfiguration> route) {
        long oneWayTravelSeconds = route.stream()
                .map(RouteStopConfiguration::travelSecondsToNext)
                .filter(seconds -> seconds != null)
                .mapToLong(Integer::longValue)
                .sum();
        long intermediateDwellSeconds = route.stream()
                .skip(1)
                .limit(Math.max(0, route.size() - 2L))
                .mapToLong(RouteStopConfiguration::dwellSeconds)
                .sum();
        long terminalDwellSeconds = Math.addExact(
                route.getFirst().dwellSeconds(),
                route.getLast().dwellSeconds()
        );
        long roundTripSeconds = Math.addExact(
                Math.multiplyExact(2, Math.addExact(oneWayTravelSeconds, intermediateDwellSeconds)),
                terminalDwellSeconds
        );
        if (roundTripSeconds <= 0) {
            throw new ServiceConfigurationException("A line round trip must have a positive duration");
        }
        return roundTripSeconds;
    }

    private int requiredFleetSize(long roundTripSeconds, int headwaySeconds) {
        long required = (roundTripSeconds + headwaySeconds - 1L) / headwaySeconds;
        long balancedFleet = Math.max(2, required);
        if (balancedFleet % 2 != 0) {
            balancedFleet++;
        }
        return Math.toIntExact(balancedFleet);
    }

    private ZonedDateTime atServiceTime(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, serviceClock.getZone());
    }

    private boolean crossesMidnight(LocalTime startTime, LocalTime endTime) {
        return endTime.isBefore(startTime);
    }

    private static final class MutableDuty {

        private final int dutyNumber;
        private final ServiceDirection initialDirection;
        private final Long originStationId;
        private final String originStationCode;
        private final String startingPeriodCode;
        private final int startingHeadwaySeconds;
        private final ZonedDateTime plannedStartAt;
        private ZonedDateTime plannedReleaseAt;

        private MutableDuty(
                int dutyNumber,
                ServiceDirection initialDirection,
                Long originStationId,
                String originStationCode,
                String startingPeriodCode,
                int startingHeadwaySeconds,
                ZonedDateTime plannedStartAt
        ) {
            this.dutyNumber = dutyNumber;
            this.initialDirection = initialDirection;
            this.originStationId = originStationId;
            this.originStationCode = originStationCode;
            this.startingPeriodCode = startingPeriodCode;
            this.startingHeadwaySeconds = startingHeadwaySeconds;
            this.plannedStartAt = plannedStartAt;
        }

        private int dutyNumber() {
            return dutyNumber;
        }

        private ZonedDateTime plannedReleaseAt() {
            return plannedReleaseAt;
        }

        private boolean isActiveAt(ZonedDateTime instant) {
            return !instant.isBefore(plannedStartAt)
                    && (plannedReleaseAt == null || instant.isBefore(plannedReleaseAt));
        }

        private void releaseAt(ZonedDateTime releaseAt) {
            plannedReleaseAt = releaseAt;
        }

        private UnassignedDuty toUnassignedDuty() {
            return new UnassignedDuty(
                    dutyNumber,
                    initialDirection,
                    originStationId,
                    originStationCode,
                    startingPeriodCode,
                    startingHeadwaySeconds,
                    plannedStartAt,
                    plannedReleaseAt
            );
        }
    }

    private record UnassignedDuty(
            int dutyNumber,
            ServiceDirection initialDirection,
            Long originStationId,
            String originStationCode,
            String startingPeriodCode,
            int startingHeadwaySeconds,
            ZonedDateTime plannedStartAt,
            ZonedDateTime plannedReleaseAt
    ) {
    }

    private record MovementSegment(
            RouteStopConfiguration previous,
            RouteStopConfiguration next,
            ServiceDirection direction,
            long dwellSeconds,
            long travelSeconds
    ) {

        private long durationSeconds() {
            return Math.addExact(dwellSeconds, travelSeconds);
        }
    }
}
