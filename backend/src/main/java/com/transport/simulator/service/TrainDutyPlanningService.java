package com.transport.simulator.service;

import com.transport.simulator.entity.LineServiceLevel;
import com.transport.simulator.entity.ServicePeriod;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.repository.LineServiceLevelRepository;
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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TrainDutyPlanningService {

    private final Clock serviceClock;
    private final ServiceOperationStateService serviceOperationStateService;
    private final LineServiceLevelRepository lineServiceLevelRepository;

    public TrainDutyPlanningService(
            Clock serviceClock,
            ServiceOperationStateService serviceOperationStateService,
            LineServiceLevelRepository lineServiceLevelRepository
    ) {
        this.serviceClock = serviceClock;
        this.serviceOperationStateService = serviceOperationStateService;
        this.lineServiceLevelRepository = lineServiceLevelRepository;
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
        List<PlannedTrainDuty> duties = generateDuties(configuration.route(), periodPlans, serviceEndsAt);

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

    private List<PlannedTrainDuty> generateDuties(
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

        return duties.stream().map(MutableDuty::toPlan).toList();
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

        private PlannedTrainDuty toPlan() {
            return new PlannedTrainDuty(
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
}
