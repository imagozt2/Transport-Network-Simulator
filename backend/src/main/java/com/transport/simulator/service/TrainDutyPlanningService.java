package com.transport.simulator.service;

import com.transport.simulator.entity.LineServiceLevel;
import com.transport.simulator.entity.ServicePeriod;
import com.transport.simulator.entity.Train;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.repository.LineServiceLevelRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.service.model.LineDutyPlan;
import com.transport.simulator.service.model.LineServiceOperationState;
import com.transport.simulator.service.model.PlannedTrainDuty;
import com.transport.simulator.service.model.ResolvedLineServiceConfiguration;
import com.transport.simulator.service.model.RouteStopConfiguration;
import com.transport.simulator.service.model.ServiceDutyPlan;
import com.transport.simulator.service.model.ServiceOperationState;
import com.transport.simulator.service.model.ServicePeriodFleetPlan;
import java.time.Clock;
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
        List<LineDutyPlan> linePlans = operationState.lines()
                .stream()
                .filter(LineServiceOperationState::serviceOpen)
                .map(this::createLinePlan)
                .toList();
        return new ServiceDutyPlan(evaluatedAt, linePlans);
    }

    private LineDutyPlan createLinePlan(LineServiceOperationState lineState) {
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
                generateDuties(configuration.route(), periodPlans, serviceEndsAt)
        );

        return new LineDutyPlan(
                lineState.lineId(),
                lineState.lineCode(),
                configuration.serviceDate(),
                serviceStartsAt,
                serviceEndsAt,
                roundTripSeconds,
                periodPlans,
                duties
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
            ZonedDateTime serviceEndsAt
    ) {
        List<MutableDuty> duties = new ArrayList<>();
        int nextDutyNumber = 1;

        for (ServicePeriodFleetPlan period : periods) {
            List<MutableDuty> activeAtPeriodStart = duties.stream()
                    .filter(duty -> duty.isActiveAt(period.startsAt()))
                    .toList();
            int fleetDifference = period.targetFleetSize() - activeAtPeriodStart.size();

            if (fleetDifference > 0) {
                long departureSpacingSeconds = Math.max(1, period.headwaySeconds() / 2L);
                ZonedDateTime nextDeparture = period.startsAt();
                for (int index = 0; index < fleetDifference && nextDeparture.isBefore(period.endsAt()); index++) {
                    ServiceDirection direction = nextDutyNumber % 2 == 1
                            ? ServiceDirection.OUTBOUND
                            : ServiceDirection.INBOUND;
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
                    nextDeparture = nextDeparture.plusSeconds(departureSpacingSeconds);
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
            List<UnassignedDuty> duties
    ) {
        List<Train> eligibleTrains = trainRepository
                .findAllByAssignedLineIdAndFleetRoleAndModelSeriesAndActiveTrueAndModelActiveTrueAndHomeDepotActiveTrueOrderByDispatchOrderAscCodeAsc(
                        lineId,
                        FleetRole.REGULAR_SERVICE,
                        REGULAR_SERVICE_SERIES
                );
        Map<Long, ZonedDateTime> availableFrom = new HashMap<>();

        return duties.stream()
                .sorted(Comparator.comparing(UnassignedDuty::plannedStartAt)
                        .thenComparingInt(UnassignedDuty::dutyNumber))
                .map(duty -> assignTrain(lineCode, duty, eligibleTrains, availableFrom))
                .sorted(Comparator.comparingInt(PlannedTrainDuty::dutyNumber))
                .toList();
    }

    private PlannedTrainDuty assignTrain(
            String lineCode,
            UnassignedDuty duty,
            List<Train> eligibleTrains,
            Map<Long, ZonedDateTime> availableFrom
    ) {
        Train train = eligibleTrains.stream()
                .filter(candidate -> candidate.getHomeDepot().getStation().getId().equals(duty.originStationId()))
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
        availableFrom.put(train.getId(), duty.plannedReleaseAt());

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
                duty.plannedReleaseAt()
        );
    }

    private long calculateRoundTripSeconds(List<RouteStopConfiguration> route) {
        long oneWayTravelSeconds = route.stream()
                .map(RouteStopConfiguration::travelSecondsToNext)
                .filter(seconds -> seconds != null)
                .mapToLong(Integer::longValue)
                .sum();
        long oneWayDwellSeconds = route.stream()
                .mapToLong(RouteStopConfiguration::dwellSeconds)
                .sum();
        long roundTripSeconds = Math.multiplyExact(2, Math.addExact(oneWayTravelSeconds, oneWayDwellSeconds));
        if (roundTripSeconds <= 0) {
            throw new ServiceConfigurationException("A line round trip must have a positive duration");
        }
        return roundTripSeconds;
    }

    private int requiredFleetSize(long roundTripSeconds, int headwaySeconds) {
        long required = (roundTripSeconds + headwaySeconds - 1L) / headwaySeconds;
        return Math.toIntExact(Math.max(1, required));
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
}
