package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Depot;
import com.transport.simulator.entity.LineDepot;
import com.transport.simulator.entity.LineServiceLevel;
import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.ServiceCalendar;
import com.transport.simulator.entity.ServicePeriod;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.OperatingDayType;
import com.transport.simulator.enums.ServicePeriodType;
import com.transport.simulator.repository.LineDepotRepository;
import com.transport.simulator.repository.LineServiceLevelRepository;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.ServiceCalendarRepository;
import com.transport.simulator.repository.ServicePeriodRepository;
import com.transport.simulator.repository.TransportLineRepository;
import com.transport.simulator.service.model.ResolvedLineServiceConfiguration;
import java.time.Clock;
import java.time.Instant;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceConfigurationServiceTests {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Europe/Madrid");
    private static final LocalDate TUESDAY = LocalDate.of(2026, 7, 21);

    @Mock
    private TransportLineRepository transportLineRepository;

    @Mock
    private ServiceCalendarRepository serviceCalendarRepository;

    @Mock
    private ServicePeriodRepository servicePeriodRepository;

    @Mock
    private LineServiceLevelRepository lineServiceLevelRepository;

    @Mock
    private LineStationRepository lineStationRepository;

    @Mock
    private LineDepotRepository lineDepotRepository;

    private TransportLine line;

    @BeforeEach
    void setUp() {
        line = mock(TransportLine.class);
        when(line.getId()).thenReturn(1L);
        when(line.getCode()).thenReturn("L1");
        when(transportLineRepository.findByCodeAndActiveTrue("L1")).thenReturn(Optional.of(line));
    }

    @Test
    void shouldResolveCurrentWeekdayPeakConfigurationUsingTheInjectedClock() {
        Clock fixedClock = Clock.fixed(
                ZonedDateTime.of(TUESDAY, LocalTime.of(8, 15), SERVICE_ZONE).toInstant(),
                SERVICE_ZONE
        );
        ServiceConfigurationService service = createService(fixedClock);
        ServiceCalendar calendar = serviceCalendar(
                10L,
                "WEEKDAY_STANDARD",
                OperatingDayType.WEEKDAY,
                LocalTime.of(5, 0),
                LocalTime.of(0, 30)
        );
        ServicePeriod peakPeriod = servicePeriod(
                20L,
                "MORNING_PEAK",
                ServicePeriodType.PEAK,
                LocalTime.of(7, 30),
                LocalTime.of(9, 30)
        );

        when(serviceCalendarRepository.findApplicableCalendars(OperatingDayType.WEEKDAY, TUESDAY.minusDays(1)))
                .thenReturn(List.of());
        when(serviceCalendarRepository.findApplicableCalendars(OperatingDayType.WEEKDAY, TUESDAY))
                .thenReturn(List.of(calendar));
        stubResolvedConfiguration(calendar, peakPeriod, 180);

        Optional<ResolvedLineServiceConfiguration> result = service.findCurrentForLine("L1");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().serviceDate()).isEqualTo(TUESDAY);
        assertThat(result.orElseThrow().calendarCode()).isEqualTo("WEEKDAY_STANDARD");
        assertThat(result.orElseThrow().periodCode()).isEqualTo("MORNING_PEAK");
        assertThat(result.orElseThrow().periodType()).isEqualTo(ServicePeriodType.PEAK);
        assertThat(result.orElseThrow().headwaySeconds()).isEqualTo(180);
        assertThat(result.orElseThrow().route()).hasSize(2);
        assertThat(result.orElseThrow().route().getFirst().travelSecondsToNext()).isEqualTo(120);
        assertThat(result.orElseThrow().depots()).singleElement()
                .satisfies(depot -> {
                    assertThat(depot.depotCode()).isEqualTo("DEP-L1");
                    assertThat(depot.dispatchPriority()).isEqualTo(1);
                    assertThat(depot.dispatchEnabled()).isTrue();
                    assertThat(depot.receptionEnabled()).isTrue();
                });
    }

    @Test
    void shouldAssignAfterMidnightOperationToThePreviousServiceDate() {
        ServiceConfigurationService service = createService(Clock.system(SERVICE_ZONE));
        LocalDate sunday = LocalDate.of(2026, 7, 26);
        LocalDate saturday = sunday.minusDays(1);
        ServiceCalendar saturdayCalendar = serviceCalendar(
                11L,
                "SATURDAY_STANDARD",
                OperatingDayType.SATURDAY,
                LocalTime.of(6, 0),
                LocalTime.of(1, 0)
        );
        ServicePeriod endPeriod = servicePeriod(
                21L,
                "END",
                ServicePeriodType.SERVICE_END,
                LocalTime.MIDNIGHT,
                LocalTime.of(1, 0)
        );

        when(serviceCalendarRepository.findApplicableCalendars(OperatingDayType.SATURDAY, saturday))
                .thenReturn(List.of(saturdayCalendar));
        stubResolvedConfiguration(saturdayCalendar, endPeriod, 720);

        Optional<ResolvedLineServiceConfiguration> result = service.findForLineAt(
                "L1",
                ZonedDateTime.of(sunday, LocalTime.of(0, 15), SERVICE_ZONE)
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().serviceDate()).isEqualTo(saturday);
        assertThat(result.orElseThrow().dayType()).isEqualTo(OperatingDayType.SATURDAY);
        assertThat(result.orElseThrow().periodType()).isEqualTo(ServicePeriodType.SERVICE_END);
        verify(serviceCalendarRepository, never())
                .findApplicableCalendars(OperatingDayType.SUNDAY_HOLIDAY, sunday);
    }

    @Test
    void shouldReturnEmptyOutsideServiceHours() {
        ServiceConfigurationService service = createService(Clock.system(SERVICE_ZONE));
        ServiceCalendar mondayCalendar = serviceCalendar(
                9L,
                "WEEKDAY_STANDARD",
                OperatingDayType.WEEKDAY,
                LocalTime.of(5, 0),
                LocalTime.of(0, 30)
        );
        ServiceCalendar tuesdayCalendar = serviceCalendar(
                10L,
                "WEEKDAY_STANDARD",
                OperatingDayType.WEEKDAY,
                LocalTime.of(5, 0),
                LocalTime.of(0, 30)
        );

        when(serviceCalendarRepository.findApplicableCalendars(OperatingDayType.WEEKDAY, TUESDAY.minusDays(1)))
                .thenReturn(List.of(mondayCalendar));
        when(serviceCalendarRepository.findApplicableCalendars(OperatingDayType.WEEKDAY, TUESDAY))
                .thenReturn(List.of(tuesdayCalendar));

        Optional<ResolvedLineServiceConfiguration> result = service.findForLineAt(
                "L1",
                ZonedDateTime.of(TUESDAY, LocalTime.of(3, 0), SERVICE_ZONE)
        );

        assertThat(result).isEmpty();
        verify(servicePeriodRepository, never())
                .findAllByServiceCalendarIdAndActiveTrueOrderByPeriodOrderAsc(10L);
        verify(lineStationRepository, never())
                .findAllByLineIdAndActiveTrueOrderByStationOrderAsc(1L);
    }

    @Test
    void shouldTreatTheServiceEndTimeAsAnExclusiveBoundary() {
        ServiceConfigurationService service = createService(Clock.system(SERVICE_ZONE));
        ServiceCalendar mondayCalendar = serviceCalendar(
                9L,
                "WEEKDAY_STANDARD",
                OperatingDayType.WEEKDAY,
                LocalTime.of(5, 0),
                LocalTime.of(0, 30)
        );
        ServiceCalendar tuesdayCalendar = serviceCalendar(
                10L,
                "WEEKDAY_STANDARD",
                OperatingDayType.WEEKDAY,
                LocalTime.of(5, 0),
                LocalTime.of(0, 30)
        );
        when(serviceCalendarRepository.findApplicableCalendars(OperatingDayType.WEEKDAY, TUESDAY.minusDays(1)))
                .thenReturn(List.of(mondayCalendar));
        when(serviceCalendarRepository.findApplicableCalendars(OperatingDayType.WEEKDAY, TUESDAY))
                .thenReturn(List.of(tuesdayCalendar));

        Optional<ResolvedLineServiceConfiguration> result = service.findForLineAt(
                "L1",
                ZonedDateTime.of(TUESDAY, LocalTime.of(0, 30), SERVICE_ZONE)
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldRejectOverlappingCalendarsForTheSameServiceDate() {
        ServiceConfigurationService service = createService(Clock.system(SERVICE_ZONE));
        ServiceCalendar firstCalendar = serviceCalendar(
                10L,
                "WEEKDAY_STANDARD",
                OperatingDayType.WEEKDAY,
                LocalTime.of(5, 0),
                LocalTime.of(0, 30)
        );
        ServiceCalendar secondCalendar = serviceCalendar(
                11L,
                "WEEKDAY_SUMMER",
                OperatingDayType.WEEKDAY,
                LocalTime.of(5, 30),
                LocalTime.of(1, 0)
        );
        when(serviceCalendarRepository.findApplicableCalendars(OperatingDayType.WEEKDAY, TUESDAY.minusDays(1)))
                .thenReturn(List.of());
        when(serviceCalendarRepository.findApplicableCalendars(OperatingDayType.WEEKDAY, TUESDAY))
                .thenReturn(List.of(firstCalendar, secondCalendar));

        assertThatThrownBy(() -> service.findForLineAt(
                "L1",
                ZonedDateTime.of(TUESDAY, LocalTime.of(8, 0), SERVICE_ZONE)
        ))
                .isInstanceOf(ServiceConfigurationException.class)
                .hasMessageContaining("More than one service calendar")
                .hasMessageContaining(TUESDAY.toString());
    }

    @Test
    void shouldRejectAnActiveServiceWithoutAMatchingPeriod() {
        ServiceConfigurationService service = createService(Clock.system(SERVICE_ZONE));
        ServiceCalendar calendar = activeTuesdayCalendar();
        when(servicePeriodRepository.findAllByServiceCalendarIdAndActiveTrueOrderByPeriodOrderAsc(10L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.findForLineAt(
                "L1",
                ZonedDateTime.of(TUESDAY, LocalTime.of(8, 0), SERVICE_ZONE)
        ))
                .isInstanceOf(ServiceConfigurationException.class)
                .hasMessageContaining("Expected one active service period")
                .hasMessageContaining(calendar.getCode());
    }

    @Test
    void shouldRejectALineWithoutAServiceLevelForTheCurrentPeriod() {
        ServiceConfigurationService service = createService(Clock.system(SERVICE_ZONE));
        ServiceCalendar calendar = activeTuesdayCalendar();
        ServicePeriod period = servicePeriod(
                20L,
                "MORNING_PEAK",
                ServicePeriodType.PEAK,
                LocalTime.of(7, 30),
                LocalTime.of(9, 30)
        );
        when(servicePeriodRepository.findAllByServiceCalendarIdAndActiveTrueOrderByPeriodOrderAsc(10L))
                .thenReturn(List.of(period));
        when(lineServiceLevelRepository
                .findAllByLineIdAndServicePeriodServiceCalendarIdAndActiveTrueOrderByServicePeriodPeriodOrderAsc(
                        1L,
                        10L
                ))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.findForLineAt(
                "L1",
                ZonedDateTime.of(TUESDAY, LocalTime.of(8, 0), SERVICE_ZONE)
        ))
                .isInstanceOf(ServiceConfigurationException.class)
                .hasMessageContaining("Expected one service level")
                .hasMessageContaining("L1")
                .hasMessageContaining("MORNING_PEAK");
    }

    @Test
    void shouldRejectARouteWithAMissingTravelTime() {
        ServiceConfigurationService service = createService(Clock.system(SERVICE_ZONE));
        ServiceCalendar calendar = activeTuesdayCalendar();
        peakPeriodWithServiceLevel(calendar, 180);
        LineStation firstStop = lineStation(1, "ST001", "Origen", null, 90);
        LineStation lastStop = lineStation(2, "ST002", "Destino", null, 90);
        when(lineStationRepository.findAllByLineIdAndActiveTrueOrderByStationOrderAsc(1L))
                .thenReturn(List.of(firstStop, lastStop));

        assertThatThrownBy(() -> service.findForLineAt(
                "L1",
                ZonedDateTime.of(TUESDAY, LocalTime.of(8, 0), SERVICE_ZONE)
        ))
                .isInstanceOf(ServiceConfigurationException.class)
                .hasMessageContaining("Missing travel time")
                .hasMessageContaining("ST001");
    }

    @Test
    void shouldRejectALineWithoutADepotEnabledForDispatch() {
        ServiceConfigurationService service = createService(Clock.system(SERVICE_ZONE));
        ServiceCalendar calendar = activeTuesdayCalendar();
        peakPeriodWithServiceLevel(calendar, 180);
        List<LineStation> route = validRoute();
        when(lineStationRepository.findAllByLineIdAndActiveTrueOrderByStationOrderAsc(1L))
                .thenReturn(route);
        LineDepot receptionOnlyDepot = lineDepot(false, true);
        when(lineDepotRepository.findAllByLineIdAndActiveTrueOrderByDispatchPriorityAsc(1L))
                .thenReturn(List.of(receptionOnlyDepot));

        assertThatThrownBy(() -> service.findForLineAt(
                "L1",
                ZonedDateTime.of(TUESDAY, LocalTime.of(8, 0), SERVICE_ZONE)
        ))
                .isInstanceOf(ServiceConfigurationException.class)
                .hasMessageContaining("no depot enabled for dispatch")
                .hasMessageContaining("L1");
    }

    @Test
    void shouldRejectAnUnknownOrInactiveLine() {
        ServiceConfigurationService service = createService(Clock.system(SERVICE_ZONE));
        when(transportLineRepository.findByCodeAndActiveTrue("L9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findForLineAt(
                "L9",
                ZonedDateTime.of(TUESDAY, LocalTime.NOON, SERVICE_ZONE)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Active line not found: L9");
    }

    private ServiceConfigurationService createService(Clock clock) {
        return new ServiceConfigurationService(
                clock,
                transportLineRepository,
                serviceCalendarRepository,
                servicePeriodRepository,
                lineServiceLevelRepository,
                lineStationRepository,
                lineDepotRepository
        );
    }

    private ServiceCalendar activeTuesdayCalendar() {
        ServiceCalendar calendar = serviceCalendar(
                10L,
                "WEEKDAY_STANDARD",
                OperatingDayType.WEEKDAY,
                LocalTime.of(5, 0),
                LocalTime.of(0, 30)
        );
        when(serviceCalendarRepository.findApplicableCalendars(OperatingDayType.WEEKDAY, TUESDAY.minusDays(1)))
                .thenReturn(List.of());
        when(serviceCalendarRepository.findApplicableCalendars(OperatingDayType.WEEKDAY, TUESDAY))
                .thenReturn(List.of(calendar));
        return calendar;
    }

    private ServicePeriod peakPeriodWithServiceLevel(ServiceCalendar calendar, int headwaySeconds) {
        ServicePeriod period = servicePeriod(
                20L,
                "MORNING_PEAK",
                ServicePeriodType.PEAK,
                LocalTime.of(7, 30),
                LocalTime.of(9, 30)
        );
        when(servicePeriodRepository.findAllByServiceCalendarIdAndActiveTrueOrderByPeriodOrderAsc(calendar.getId()))
                .thenReturn(List.of(period));
        LineServiceLevel level = mock(LineServiceLevel.class);
        when(level.getServicePeriod()).thenReturn(period);
        when(level.getHeadwaySeconds()).thenReturn(headwaySeconds);
        when(lineServiceLevelRepository
                .findAllByLineIdAndServicePeriodServiceCalendarIdAndActiveTrueOrderByServicePeriodPeriodOrderAsc(
                        1L,
                        calendar.getId()
                ))
                .thenReturn(List.of(level));
        return period;
    }

    private void stubResolvedConfiguration(
            ServiceCalendar calendar,
            ServicePeriod period,
            int headwaySeconds
    ) {
        when(servicePeriodRepository.findAllByServiceCalendarIdAndActiveTrueOrderByPeriodOrderAsc(calendar.getId()))
                .thenReturn(List.of(period));
        LineServiceLevel level = mock(LineServiceLevel.class);
        when(level.getServicePeriod()).thenReturn(period);
        when(level.getHeadwaySeconds()).thenReturn(headwaySeconds);
        when(lineServiceLevelRepository
                .findAllByLineIdAndServicePeriodServiceCalendarIdAndActiveTrueOrderByServicePeriodPeriodOrderAsc(
                        1L,
                        calendar.getId()
                ))
                .thenReturn(List.of(level));
        List<LineStation> route = validRoute();
        LineDepot operationalDepot = lineDepot(true, true);
        when(lineStationRepository.findAllByLineIdAndActiveTrueOrderByStationOrderAsc(1L))
                .thenReturn(route);
        when(lineDepotRepository.findAllByLineIdAndActiveTrueOrderByDispatchPriorityAsc(1L))
                .thenReturn(List.of(operationalDepot));
    }

    private List<LineStation> validRoute() {
        return List.of(
                lineStation(1, "ST001", "Origen", 120, 90),
                lineStation(2, "ST002", "Destino", null, 90)
        );
    }

    private ServiceCalendar serviceCalendar(
            Long id,
            String code,
            OperatingDayType dayType,
            LocalTime start,
            LocalTime end
    ) {
        ServiceCalendar calendar = mock(ServiceCalendar.class);
        when(calendar.getId()).thenReturn(id);
        when(calendar.getCode()).thenReturn(code);
        when(calendar.getDayType()).thenReturn(dayType);
        when(calendar.getServiceStartTime()).thenReturn(start);
        when(calendar.getServiceEndTime()).thenReturn(end);
        return calendar;
    }

    private ServicePeriod servicePeriod(
            Long id,
            String code,
            ServicePeriodType periodType,
            LocalTime start,
            LocalTime end
    ) {
        ServicePeriod period = mock(ServicePeriod.class);
        when(period.getId()).thenReturn(id);
        when(period.getCode()).thenReturn(code);
        when(period.getPeriodType()).thenReturn(periodType);
        when(period.getStartTime()).thenReturn(start);
        when(period.getEndTime()).thenReturn(end);
        return period;
    }

    private LineStation lineStation(
            int stationOrder,
            String stationCode,
            String stationName,
            Integer travelSeconds,
            int dwellSeconds
    ) {
        Station station = mock(Station.class);
        when(station.getId()).thenReturn((long) stationOrder);
        when(station.getCode()).thenReturn(stationCode);
        when(station.getName()).thenReturn(stationName);

        LineStation stop = mock(LineStation.class);
        when(stop.getStation()).thenReturn(station);
        when(stop.getStationOrder()).thenReturn(stationOrder);
        when(stop.getTravelSecondsToNext()).thenReturn(travelSeconds);
        when(stop.getDwellSeconds()).thenReturn(dwellSeconds);
        return stop;
    }

    private LineDepot lineDepot(boolean dispatchEnabled, boolean receptionEnabled) {
        Depot depot = mock(Depot.class);
        when(depot.getId()).thenReturn(1L);
        when(depot.getCode()).thenReturn("DEP-L1");
        when(depot.getName()).thenReturn("Cochera L1");
        if (dispatchEnabled && receptionEnabled) {
            Station station = mock(Station.class);
            when(station.getId()).thenReturn(100L);
            when(station.getCode()).thenReturn("ST001");
            when(depot.getStation()).thenReturn(station);
        }

        LineDepot lineDepot = mock(LineDepot.class);
        when(lineDepot.getDepot()).thenReturn(depot);
        if (dispatchEnabled && receptionEnabled) {
            Station dispatchTerminal = depot.getStation();
            when(lineDepot.getDispatchTerminalStation()).thenReturn(dispatchTerminal);
        }
        when(lineDepot.getDispatchPriority()).thenReturn(1);
        when(lineDepot.isDispatchEnabled()).thenReturn(dispatchEnabled);
        when(lineDepot.isReceptionEnabled()).thenReturn(receptionEnabled);
        return lineDepot;
    }
}
