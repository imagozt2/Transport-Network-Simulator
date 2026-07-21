package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.OperatingDayType;
import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.ServicePeriodType;
import com.transport.simulator.repository.TransportLineRepository;
import com.transport.simulator.service.model.ResolvedLineServiceConfiguration;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ServiceOperationStateServiceTests {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Europe/Madrid");

    @Test
    void shouldCalculateAnAfterMidnightServiceFromThePreviousOperatingDay() {
        ZonedDateTime evaluatedAt = ZonedDateTime.of(
                LocalDate.of(2026, 7, 22),
                LocalTime.of(0, 30),
                SERVICE_ZONE
        );
        TransportLineRepository lineRepository = mock(TransportLineRepository.class);
        ServiceConfigurationService configurationService = mock(ServiceConfigurationService.class);
        TransportLine line = mock(TransportLine.class);
        when(line.getId()).thenReturn(1L);
        when(line.getCode()).thenReturn("L1");
        when(lineRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(line));
        when(configurationService.findForLineAt("L1", evaluatedAt)).thenReturn(Optional.of(
                configuration(
                        evaluatedAt.toLocalDate().minusDays(1),
                        LocalTime.of(23, 0),
                        LocalTime.of(2, 0),
                        ServicePeriodType.REGULAR
                )
        ));
        ServiceOperationStateService service = new ServiceOperationStateService(
                Clock.fixed(evaluatedAt.toInstant(), SERVICE_ZONE),
                lineRepository,
                configurationService
        );

        var state = service.getCurrentState();

        assertThat(state.phase()).isEqualTo(ServiceOperationPhase.OPERATING);
        assertThat(state.activeLineCount()).isEqualTo(1);
        assertThat(state.lines()).singleElement().satisfies(lineState -> {
            assertThat(lineState.elapsedServiceSeconds()).isEqualTo(5_400);
            assertThat(lineState.remainingServiceSeconds()).isEqualTo(5_400);
            assertThat(lineState.configuration().orElseThrow().serviceDate())
                    .isEqualTo(evaluatedAt.toLocalDate().minusDays(1));
        });
    }

    @Test
    void shouldMarkTheNetworkClosedWhenNoLineHasAnApplicableSchedule() {
        ZonedDateTime evaluatedAt = ZonedDateTime.of(
                LocalDate.of(2026, 7, 22),
                LocalTime.of(3, 0),
                SERVICE_ZONE
        );
        TransportLineRepository lineRepository = mock(TransportLineRepository.class);
        ServiceConfigurationService configurationService = mock(ServiceConfigurationService.class);
        TransportLine line = mock(TransportLine.class);
        when(line.getId()).thenReturn(1L);
        when(line.getCode()).thenReturn("L1");
        when(lineRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(line));
        when(configurationService.findForLineAt("L1", evaluatedAt)).thenReturn(Optional.empty());
        ServiceOperationStateService service = new ServiceOperationStateService(
                Clock.fixed(evaluatedAt.toInstant(), SERVICE_ZONE),
                lineRepository,
                configurationService
        );

        var state = service.getCurrentState();

        assertThat(state.phase()).isEqualTo(ServiceOperationPhase.CLOSED);
        assertThat(state.activeLineCount()).isZero();
        assertThat(state.lines()).singleElement().satisfies(lineState -> {
            assertThat(lineState.serviceOpen()).isFalse();
            assertThat(lineState.configuration()).isEmpty();
        });
    }

    private ResolvedLineServiceConfiguration configuration(
            LocalDate serviceDate,
            LocalTime serviceStart,
            LocalTime serviceEnd,
            ServicePeriodType periodType
    ) {
        return new ResolvedLineServiceConfiguration(
                1L,
                "L1",
                serviceDate,
                "WEEKDAY_STANDARD",
                OperatingDayType.WEEKDAY,
                serviceStart,
                serviceEnd,
                "CURRENT",
                periodType,
                serviceStart,
                serviceEnd,
                220,
                List.of(),
                List.of()
        );
    }
}
