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
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.repository.DeviceRepository;
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

    private DeviceEventSimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new DeviceEventSimulationService(
                deviceRepository,
                eventGenerator,
                registrationService
        );
    }

    @Test
    void shouldGenerateAndRegisterDistinctEventsUpToTheConfiguredAmount() {
        Device first = device("DEV-001");
        Device second = device("DEV-002");
        Device third = device("DEV-003");
        when(deviceRepository.findAllByActiveTrueOrderByCodeAsc())
                .thenReturn(List.of(first, second, third));
        when(eventGenerator.generate(any(Device.class)))
                .thenAnswer(invocation -> event(invocation.getArgument(0)));
        ArgumentCaptor<DeviceEvent> captor = ArgumentCaptor.forClass(DeviceEvent.class);

        int generated = simulationService.runCycle(2);

        assertThat(generated).isEqualTo(2);
        verify(registrationService, times(2)).register(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DeviceEvent::deviceCode)
                .doesNotHaveDuplicates()
                .allMatch(code -> code.startsWith("DEV-"));
    }

    @Test
    void shouldNotRegisterAnythingWhenThereAreNoActiveDevices() {
        when(deviceRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of());

        assertThat(simulationService.runCycle(5)).isZero();

        verify(eventGenerator, never()).generate(any());
        verify(registrationService, never()).register(any());
    }

    @Test
    void shouldClampAZeroConfigurationToOneEvent() {
        Device device = device("DEV-001");
        DeviceEvent generatedEvent = event(device);
        when(deviceRepository.findAllByActiveTrueOrderByCodeAsc())
                .thenReturn(List.of(device));
        when(eventGenerator.generate(device)).thenReturn(generatedEvent);

        assertThat(simulationService.runCycle(0)).isEqualTo(1);
        verify(registrationService).register(any());
    }

    private Device device(String code) {
        Device device = mock(Device.class);
        lenient().when(device.getCode()).thenReturn(code);
        return device;
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
