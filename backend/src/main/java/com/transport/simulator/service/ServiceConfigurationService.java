package com.transport.simulator.service;

import com.transport.simulator.entity.LineDepot;
import com.transport.simulator.entity.LineServiceLevel;
import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.ServiceCalendar;
import com.transport.simulator.entity.ServicePeriod;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.OperatingDayType;
import com.transport.simulator.repository.LineDepotRepository;
import com.transport.simulator.repository.LineServiceLevelRepository;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.ServiceCalendarRepository;
import com.transport.simulator.repository.ServicePeriodRepository;
import com.transport.simulator.repository.TransportLineRepository;
import com.transport.simulator.service.model.LineDepotConfiguration;
import com.transport.simulator.service.model.ResolvedLineServiceConfiguration;
import com.transport.simulator.service.model.RouteStopConfiguration;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServiceConfigurationService {

    private final Clock serviceClock;
    private final TransportLineRepository transportLineRepository;
    private final ServiceCalendarRepository serviceCalendarRepository;
    private final ServicePeriodRepository servicePeriodRepository;
    private final LineServiceLevelRepository lineServiceLevelRepository;
    private final LineStationRepository lineStationRepository;
    private final LineDepotRepository lineDepotRepository;

    public ServiceConfigurationService(
            Clock serviceClock,
            TransportLineRepository transportLineRepository,
            ServiceCalendarRepository serviceCalendarRepository,
            ServicePeriodRepository servicePeriodRepository,
            LineServiceLevelRepository lineServiceLevelRepository,
            LineStationRepository lineStationRepository,
            LineDepotRepository lineDepotRepository
    ) {
        this.serviceClock = serviceClock;
        this.transportLineRepository = transportLineRepository;
        this.serviceCalendarRepository = serviceCalendarRepository;
        this.servicePeriodRepository = servicePeriodRepository;
        this.lineServiceLevelRepository = lineServiceLevelRepository;
        this.lineStationRepository = lineStationRepository;
        this.lineDepotRepository = lineDepotRepository;
    }

    public Optional<ResolvedLineServiceConfiguration> findCurrentForLine(String lineCode) {
        return findForLineAt(lineCode, ZonedDateTime.now(serviceClock));
    }

    public Optional<ResolvedLineServiceConfiguration> findForLineAt(
            String lineCode,
            ZonedDateTime requestedDateTime
    ) {
        TransportLine line = transportLineRepository.findByCodeAndActiveTrue(lineCode)
                .orElseThrow(() -> new IllegalArgumentException("Active line not found: " + lineCode));

        ZonedDateTime operationDateTime = requestedDateTime.withZoneSameInstant(serviceClock.getZone());
        Optional<ServiceDay> serviceDay = resolveServiceDay(operationDateTime);
        if (serviceDay.isEmpty()) {
            return Optional.empty();
        }

        ServiceCalendar calendar = serviceDay.get().calendar();
        LocalTime operationTime = operationDateTime.toLocalTime();
        ServicePeriod period = resolvePeriod(calendar, operationTime);
        LineServiceLevel serviceLevel = resolveServiceLevel(line, calendar, period);
        List<RouteStopConfiguration> route = resolveRoute(line);
        List<LineDepotConfiguration> depots = resolveDepots(line);

        return Optional.of(new ResolvedLineServiceConfiguration(
                line.getId(),
                line.getCode(),
                serviceDay.get().serviceDate(),
                calendar.getCode(),
                calendar.getDayType(),
                calendar.getServiceStartTime(),
                calendar.getServiceEndTime(),
                period.getCode(),
                period.getPeriodType(),
                period.getStartTime(),
                period.getEndTime(),
                serviceLevel.getHeadwaySeconds(),
                route,
                depots
        ));
    }

    private Optional<ServiceDay> resolveServiceDay(ZonedDateTime dateTime) {
        LocalDate currentDate = dateTime.toLocalDate();
        LocalTime currentTime = dateTime.toLocalTime();

        LocalDate previousDate = currentDate.minusDays(1);
        Optional<ServiceCalendar> previousCalendar = findCalendar(previousDate);
        if (previousCalendar.isPresent()
                && crossesMidnight(previousCalendar.get())
                && currentTime.isBefore(previousCalendar.get().getServiceEndTime())) {
            return Optional.of(new ServiceDay(previousDate, previousCalendar.get()));
        }

        Optional<ServiceCalendar> currentCalendar = findCalendar(currentDate);
        if (currentCalendar.isPresent() && serviceStartsToday(currentCalendar.get(), currentTime)) {
            return Optional.of(new ServiceDay(currentDate, currentCalendar.get()));
        }

        return Optional.empty();
    }

    private Optional<ServiceCalendar> findCalendar(LocalDate serviceDate) {
        List<ServiceCalendar> calendars = serviceCalendarRepository.findApplicableCalendars(
                operatingDayType(serviceDate.getDayOfWeek()),
                serviceDate
        );
        if (calendars.size() > 1) {
            throw new ServiceConfigurationException(
                    "More than one service calendar applies to " + serviceDate
            );
        }
        return calendars.stream().findFirst();
    }

    private ServicePeriod resolvePeriod(ServiceCalendar calendar, LocalTime operationTime) {
        List<ServicePeriod> matchingPeriods = servicePeriodRepository
                .findAllByServiceCalendarIdAndActiveTrueOrderByPeriodOrderAsc(calendar.getId())
                .stream()
                .filter(period -> contains(period.getStartTime(), period.getEndTime(), operationTime))
                .toList();

        if (matchingPeriods.size() != 1) {
            throw new ServiceConfigurationException(
                    "Expected one active service period for calendar " + calendar.getCode()
                            + " at " + operationTime + " but found " + matchingPeriods.size()
            );
        }
        return matchingPeriods.getFirst();
    }

    private LineServiceLevel resolveServiceLevel(
            TransportLine line,
            ServiceCalendar calendar,
            ServicePeriod currentPeriod
    ) {
        List<LineServiceLevel> levels = lineServiceLevelRepository
                .findAllByLineIdAndServicePeriodServiceCalendarIdAndActiveTrueOrderByServicePeriodPeriodOrderAsc(
                        line.getId(),
                        calendar.getId()
                );

        List<LineServiceLevel> matchingLevels = levels.stream()
                .filter(level -> level.getServicePeriod().getId().equals(currentPeriod.getId()))
                .toList();
        if (matchingLevels.size() != 1) {
            throw new ServiceConfigurationException(
                    "Expected one service level for line " + line.getCode()
                            + " and period " + currentPeriod.getCode()
                            + " but found " + matchingLevels.size()
            );
        }
        return matchingLevels.getFirst();
    }

    private List<RouteStopConfiguration> resolveRoute(TransportLine line) {
        List<LineStation> stops = lineStationRepository
                .findAllByLineIdAndActiveTrueOrderByStationOrderAsc(line.getId());
        if (stops.size() < 2) {
            throw new ServiceConfigurationException(
                    "Line " + line.getCode() + " must contain at least two active stations"
            );
        }

        for (int index = 0; index < stops.size() - 1; index++) {
            if (stops.get(index).getTravelSecondsToNext() == null) {
                throw new ServiceConfigurationException(
                        "Missing travel time after station " + stops.get(index).getStation().getCode()
                                + " on line " + line.getCode()
                );
            }
        }

        return stops.stream()
                .map(stop -> new RouteStopConfiguration(
                        stop.getStation().getId(),
                        stop.getStation().getCode(),
                        stop.getStation().getName(),
                        stop.getStationOrder(),
                        stop.getTravelSecondsToNext(),
                        stop.getDwellSeconds()
                ))
                .toList();
    }

    private List<LineDepotConfiguration> resolveDepots(TransportLine line) {
        List<LineDepot> lineDepots = lineDepotRepository
                .findAllByLineIdAndActiveTrueOrderByDispatchPriorityAsc(line.getId());
        if (lineDepots.stream().noneMatch(LineDepot::isDispatchEnabled)) {
            throw new ServiceConfigurationException(
                    "Line " + line.getCode() + " has no depot enabled for dispatch"
            );
        }
        if (lineDepots.stream().noneMatch(LineDepot::isReceptionEnabled)) {
            throw new ServiceConfigurationException(
                    "Line " + line.getCode() + " has no depot enabled for reception"
            );
        }

        return lineDepots.stream()
                .map(lineDepot -> new LineDepotConfiguration(
                        lineDepot.getDepot().getId(),
                        lineDepot.getDepot().getCode(),
                        lineDepot.getDepot().getName(),
                        lineDepot.getDispatchPriority(),
                        lineDepot.isDispatchEnabled(),
                        lineDepot.isReceptionEnabled()
                ))
                .toList();
    }

    private boolean serviceStartsToday(ServiceCalendar calendar, LocalTime currentTime) {
        if (crossesMidnight(calendar)) {
            return !currentTime.isBefore(calendar.getServiceStartTime());
        }
        return contains(calendar.getServiceStartTime(), calendar.getServiceEndTime(), currentTime);
    }

    private boolean crossesMidnight(ServiceCalendar calendar) {
        return calendar.getServiceEndTime().isBefore(calendar.getServiceStartTime());
    }

    private boolean contains(LocalTime start, LocalTime end, LocalTime value) {
        if (end.isAfter(start)) {
            return !value.isBefore(start) && value.isBefore(end);
        }
        return !value.isBefore(start) || value.isBefore(end);
    }

    private OperatingDayType operatingDayType(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SATURDAY -> OperatingDayType.SATURDAY;
            case SUNDAY -> OperatingDayType.SUNDAY_HOLIDAY;
            default -> OperatingDayType.WEEKDAY;
        };
    }

    private record ServiceDay(LocalDate serviceDate, ServiceCalendar calendar) {
    }
}
