package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.repository.DeviceEventLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OperationalLogsQueryServiceTests {

    @Mock
    private DeviceEventLogRepository eventLogRepository;

    private OperationalLogsQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new OperationalLogsQueryService(eventLogRepository);
    }

    @Test
    void shouldCombineDeviceTypeWithOtherFiltersAndNormalizePagination() {
        LocalDateTime occurredFrom = LocalDateTime.of(2026, 7, 29, 10, 0);
        LocalDateTime occurredTo = LocalDateTime.of(2026, 7, 29, 12, 0);
        when(eventLogRepository.findFiltered(
                eq(LogOrigin.DEVICE_SIMULATION),
                eq(LogSeverity.INFO),
                eq(DeviceEventType.VALIDATION_ACCEPTED),
                eq(DeviceType.ENTRY_VALIDATOR),
                eq("VAL-ST001-01"),
                eq("ST001"),
                eq(occurredFrom),
                eq(occurredTo),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.<DeviceEventLog>of(),
                PageRequest.of(0, 100),
                125
        ));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        var response = queryService.getLogs(
                -3,
                500,
                LogOrigin.DEVICE_SIMULATION,
                LogSeverity.INFO,
                DeviceEventType.VALIDATION_ACCEPTED,
                DeviceType.ENTRY_VALIDATOR,
                "  VAL-ST001-01  ",
                " ST001 ",
                occurredFrom,
                occurredTo
        );

        verify(eventLogRepository).findFiltered(
                eq(LogOrigin.DEVICE_SIMULATION),
                eq(LogSeverity.INFO),
                eq(DeviceEventType.VALIDATION_ACCEPTED),
                eq(DeviceType.ENTRY_VALIDATOR),
                eq("VAL-ST001-01"),
                eq("ST001"),
                eq(occurredFrom),
                eq(occurredTo),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("occurredAt")).isNotNull();
        assertThat(response.totalElements()).isEqualTo(125);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldRejectAnInvertedDateRangeBeforeQueryingTheRepository() {
        LocalDateTime occurredFrom = LocalDateTime.of(2026, 7, 29, 12, 0);
        LocalDateTime occurredTo = LocalDateTime.of(2026, 7, 29, 10, 0);

        assertThatThrownBy(() -> queryService.getLogs(
                0,
                25,
                null,
                null,
                null,
                DeviceType.TICKET_MACHINE,
                null,
                null,
                occurredFrom,
                occurredTo
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("occurredFrom");

        verify(eventLogRepository, never()).findFiltered(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }
}
