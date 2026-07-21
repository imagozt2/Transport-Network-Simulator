package com.transport.simulator.service;

import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.ServicePeriodType;
import com.transport.simulator.repository.TransportLineRepository;
import com.transport.simulator.service.model.LineServiceOperationState;
import com.transport.simulator.service.model.ResolvedLineServiceConfiguration;
import com.transport.simulator.service.model.ServiceOperationState;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServiceOperationStateService {

    private final Clock serviceClock;
    private final TransportLineRepository transportLineRepository;
    private final ServiceConfigurationService serviceConfigurationService;

    public ServiceOperationStateService(
            Clock serviceClock,
            TransportLineRepository transportLineRepository,
            ServiceConfigurationService serviceConfigurationService
    ) {
        this.serviceClock = serviceClock;
        this.transportLineRepository = transportLineRepository;
        this.serviceConfigurationService = serviceConfigurationService;
    }

    public ServiceOperationState getCurrentState() {
        return getStateAt(ZonedDateTime.now(serviceClock));
    }

    public ServiceOperationState getStateAt(ZonedDateTime requestedDateTime) {
        ZonedDateTime evaluatedAt = requestedDateTime.withZoneSameInstant(serviceClock.getZone());
        List<LineServiceOperationState> lineStates = transportLineRepository
                .findAllByActiveTrueOrderByCodeAsc()
                .stream()
                .map(line -> resolveLineState(line, evaluatedAt))
                .toList();

        int activeLineCount = Math.toIntExact(
                lineStates.stream().filter(LineServiceOperationState::serviceOpen).count()
        );

        return new ServiceOperationState(
                evaluatedAt,
                resolveNetworkPhase(lineStates),
                activeLineCount,
                lineStates
        );
    }

    private LineServiceOperationState resolveLineState(TransportLine line, ZonedDateTime evaluatedAt) {
        Optional<ResolvedLineServiceConfiguration> resolvedConfiguration = serviceConfigurationService
                .findForLineAt(line.getCode(), evaluatedAt);
        if (resolvedConfiguration.isEmpty()) {
            return LineServiceOperationState.closed(line.getId(), line.getCode());
        }

        ResolvedLineServiceConfiguration configuration = resolvedConfiguration.orElseThrow();
        ZonedDateTime serviceStart = atServiceTime(
                configuration.serviceDate(),
                configuration.serviceStartTime()
        );
        LocalDate serviceEndDate = crossesMidnight(
                configuration.serviceStartTime(),
                configuration.serviceEndTime()
        )
                ? configuration.serviceDate().plusDays(1)
                : configuration.serviceDate();
        ZonedDateTime serviceEnd = atServiceTime(serviceEndDate, configuration.serviceEndTime());

        return new LineServiceOperationState(
                line.getId(),
                line.getCode(),
                phaseFor(configuration.periodType()),
                resolvedConfiguration,
                nonNegativeSecondsBetween(serviceStart, evaluatedAt),
                nonNegativeSecondsBetween(evaluatedAt, serviceEnd)
        );
    }

    private ServiceOperationPhase resolveNetworkPhase(List<LineServiceOperationState> lineStates) {
        List<LineServiceOperationState> activeLines = lineStates.stream()
                .filter(LineServiceOperationState::serviceOpen)
                .toList();
        if (activeLines.isEmpty()) {
            return ServiceOperationPhase.CLOSED;
        }
        if (activeLines.stream().allMatch(line -> line.phase() == ServiceOperationPhase.STARTING)) {
            return ServiceOperationPhase.STARTING;
        }
        if (activeLines.stream().allMatch(line -> line.phase() == ServiceOperationPhase.ENDING)) {
            return ServiceOperationPhase.ENDING;
        }
        return ServiceOperationPhase.OPERATING;
    }

    private ServiceOperationPhase phaseFor(ServicePeriodType periodType) {
        return switch (periodType) {
            case SERVICE_START -> ServiceOperationPhase.STARTING;
            case SERVICE_END -> ServiceOperationPhase.ENDING;
            case OFF_PEAK, PEAK, REGULAR -> ServiceOperationPhase.OPERATING;
        };
    }

    private ZonedDateTime atServiceTime(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, serviceClock.getZone());
    }

    private boolean crossesMidnight(LocalTime startTime, LocalTime endTime) {
        return endTime.isBefore(startTime);
    }

    private long nonNegativeSecondsBetween(ZonedDateTime start, ZonedDateTime end) {
        return Math.max(0, Duration.between(start, end).toSeconds());
    }
}
