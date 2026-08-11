package com.transport.simulator.service.deviceevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.entity.Station;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceEventSource;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.repository.DeviceEventLogRepository;
import com.transport.simulator.repository.DeviceRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceEventRegistrationServiceTests {

    private static final LocalDateTime OCCURRED_AT =
            LocalDateTime.of(2026, 7, 23, 10, 15, 30);

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceEventLogRepository eventLogRepository;

    private DeviceEventRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new DeviceEventRegistrationService(
                deviceRepository,
                eventLogRepository,
                new DeviceStatusTransitionPolicy()
        );
    }

    @Test
    void shouldPersistTheEventAndUpdateItsDeviceAtomically() {
        Device device = device("TVM-ST001-01", DeviceStatus.OFFLINE);
        DeviceEvent event = event(
                DeviceEventType.DEVICE_ONLINE,
                LogSeverity.INFO,
                "Conexión restablecida"
        );
        when(deviceRepository.findByCodeAndActiveTrue(event.deviceCode()))
                .thenReturn(Optional.of(device));
        when(eventLogRepository.save(any(DeviceEventLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceEventLog savedLog = registrationService.register(event);

        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(device.getLastConnectionAt()).isEqualTo(OCCURRED_AT);
        assertThat(savedLog.getDevice()).isSameAs(device);
        assertThat(savedLog.getStation()).isSameAs(device.getStation());
        assertThat(savedLog.getOrigin()).isEqualTo(LogOrigin.DEVICE_SIMULATION);
        assertThat(savedLog.getSource()).isEqualTo(DeviceEventSource.SIMULATED);
        assertThat(savedLog.getEventType()).isEqualTo(DeviceEventType.DEVICE_ONLINE);
    }

    @Test
    void shouldApplyErrorAndMaintenanceTransitions() {
        Device device = device("TVM-ST001-01", DeviceStatus.ONLINE);
        when(deviceRepository.findByCodeAndActiveTrue(device.getCode()))
                .thenReturn(Optional.of(device));
        when(eventLogRepository.save(any(DeviceEventLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        registrationService.register(event(
                DeviceEventType.VALIDATION_FAILED,
                LogSeverity.ERROR,
                "Fallo de validación"
        ));
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ERROR);

        registrationService.register(event(
                DeviceEventType.DEVICE_MAINTENANCE_STARTED,
                LogSeverity.WARNING,
                "Mantenimiento iniciado"
        ));
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.MAINTENANCE);
    }

    @Test
    void shouldRejectEventsFromUnknownOrInactiveDevices() {
        DeviceEvent event = event(
                DeviceEventType.DEVICE_ONLINE,
                LogSeverity.INFO,
                "Conexión"
        );
        when(deviceRepository.findByCodeAndActiveTrue(event.deviceCode()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> registrationService.register(event))
                .isInstanceOf(UnknownDeviceException.class)
                .hasMessageContaining(event.deviceCode());
        verify(eventLogRepository, never()).save(any());
    }

    @Test
    void shouldPersistTheMachineAsTheOnlyEmitterAndDeriveItsStation() {
        Device device = device("TVM-ST001-01", DeviceStatus.ONLINE);
        DeviceEvent event = event(
                DeviceEventType.TICKET_PURCHASE_COMPLETED,
                LogSeverity.INFO,
                "Compra completada"
        );
        when(deviceRepository.findByCodeAndActiveTrue(event.deviceCode()))
                .thenReturn(Optional.of(device));
        when(eventLogRepository.save(any(DeviceEventLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<DeviceEventLog> captor =
                ArgumentCaptor.forClass(DeviceEventLog.class);

        registrationService.register(event);

        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDevice()).isSameAs(device);
        assertThat(captor.getValue().getStation()).isSameAs(device.getStation());
    }

    private Device device(String code, DeviceStatus status) {
        Device device = BeanUtils.instantiateClass(Device.class);
        Station station = new Station("ST001", "Estación Central");
        ReflectionTestUtils.setField(device, "code", code);
        ReflectionTestUtils.setField(device, "name", "Máquina " + code);
        ReflectionTestUtils.setField(device, "station", station);
        ReflectionTestUtils.setField(device, "status", status);
        ReflectionTestUtils.setField(device, "type", DeviceType.TICKET_MACHINE);
        ReflectionTestUtils.setField(device, "active", true);
        return device;
    }

    private DeviceEvent event(
            DeviceEventType type,
            LogSeverity severity,
            String message
    ) {
        return new DeviceEvent(
                "TVM-ST001-01",
                LogOrigin.DEVICE_SIMULATION,
                com.transport.simulator.enums.DeviceEventSource.SIMULATED,
                type,
                severity,
                message,
                OCCURRED_AT,
                null,
                "{}"
        );
    }
}
