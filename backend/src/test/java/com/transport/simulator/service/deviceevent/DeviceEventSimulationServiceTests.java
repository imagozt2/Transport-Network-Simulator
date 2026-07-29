package com.transport.simulator.service.deviceevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Device;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.service.ServiceOperationStateService;
import com.transport.simulator.service.model.ServiceOperationState;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceEventSimulationServiceTests {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private SimulatedDeviceEventGenerator eventGenerator;
    @Mock
    private DeviceEventRegistrationService registrationService;
    @Mock
    private ServiceOperationStateService serviceOperationStateService;

    private DeviceEventSimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new DeviceEventSimulationService(
                deviceRepository,
                eventGenerator,
                registrationService,
                serviceOperationStateService
        );
    }

    @Test
    void shouldGenerateAndRegisterOneOperationalEventPerCycle() {
        Device first = device("DEV-001");
        Device second = device("DEV-002");
        Device third = device("DEV-003");
        when(deviceRepository.findAllByActiveTrueOrderByCodeAsc())
                .thenReturn(List.of(first, second, third));
        serviceOpen(true);
        when(eventGenerator.generateOperationalActivity(any(Device.class)))
                .thenAnswer(invocation -> event(invocation.getArgument(0)));
        ArgumentCaptor<DeviceEvent> captor = ArgumentCaptor.forClass(DeviceEvent.class);

        int generated = simulationService.runCycle();

        assertThat(generated).isEqualTo(1);
        verify(registrationService).register(captor.capture());
        assertThat(captor.getValue().deviceCode()).startsWith("DEV-");
    }

    @Test
    void shouldNotRegisterAnythingWhenThereAreNoActiveDevices() {
        when(deviceRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of());

        assertThat(simulationService.runCycle()).isZero();

        verify(eventGenerator, never()).generateOperationalActivity(any());
        verify(registrationService, never()).register(any());
    }

    @Test
    void shouldSynchronizeEveryDeviceWithTheServiceOpeningBeforeActivity() {
        Device first = device("DEV-001", DeviceStatus.OFFLINE);
        Device second = device("DEV-002", DeviceStatus.ERROR);
        when(deviceRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(first, second));
        serviceOpen(true);
        when(eventGenerator.generateServiceState(any(Device.class), org.mockito.ArgumentMatchers.eq(true)))
                .thenAnswer(invocation -> event(invocation.getArgument(0)));
        when(eventGenerator.generateOperationalActivity(any(Device.class)))
                .thenAnswer(invocation -> event(invocation.getArgument(0)));

        int generated = simulationService.runCycle();

        assertThat(generated).isEqualTo(3);
        verify(eventGenerator, times(2)).generateServiceState(any(Device.class), org.mockito.ArgumentMatchers.eq(true));
        verify(eventGenerator).generateOperationalActivity(any(Device.class));
    }

    @Test
    void shouldSwitchEveryDeviceOfflineAndGenerateNoActivityWhenServiceCloses() {
        Device first = device("DEV-001", DeviceStatus.ONLINE);
        Device second = device("DEV-002", DeviceStatus.ONLINE);
        when(deviceRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(first, second));
        serviceOpen(false);
        when(eventGenerator.generateServiceState(any(Device.class), org.mockito.ArgumentMatchers.eq(false)))
                .thenAnswer(invocation -> event(invocation.getArgument(0)));

        assertThat(simulationService.runCycle()).isEqualTo(2);
        verify(eventGenerator, times(2)).generateServiceState(any(Device.class), org.mockito.ArgumentMatchers.eq(false));
        verify(eventGenerator, never()).generateOperationalActivity(any());
    }

    private Device device(String code) {
        return device(code, DeviceStatus.ONLINE);
    }

    private Device device(String code, DeviceStatus status) {
        Device device = mock(Device.class);
        lenient().when(device.getCode()).thenReturn(code);
        lenient().when(device.getStatus()).thenReturn(status);
        return device;
    }

    private void serviceOpen(boolean open) {
        ServiceOperationState state = mock(ServiceOperationState.class);
        when(state.serviceOpen()).thenReturn(open);
        when(serviceOperationStateService.getCurrentState()).thenReturn(state);
    }

    private DeviceEvent event(Device device) {
        return new DeviceEvent(
                device.getCode(),
                LogOrigin.DEVICE_SIMULATION,
                DeviceEventType.DEVICE_ONLINE,
                LogSeverity.INFO,
                "Conexión simulada",
                LocalDateTime.of(2026, 7, 23, 10, 0),
                null,
                "{}"
        );
    }
}
