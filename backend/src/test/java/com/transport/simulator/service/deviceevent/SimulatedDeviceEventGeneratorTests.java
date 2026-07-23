package com.transport.simulator.service.deviceevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.Station;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.LogOrigin;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SimulatedDeviceEventGeneratorTests {

    private static final Instant NOW = Instant.parse("2026-07-23T08:00:00Z");
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Europe/Madrid");
    private final SimulatedDeviceEventGenerator generator =
            new SimulatedDeviceEventGenerator(Clock.fixed(NOW, SERVICE_ZONE));

    @Test
    void shouldRestoreAnOfflineDeviceBeforeGeneratingOperationalActivity() {
        DeviceEvent event = generator.generate(device(
                "TVM-ST001-01",
                DeviceType.TICKET_MACHINE,
                DeviceStatus.OFFLINE
        ));

        assertThat(event.type()).isEqualTo(DeviceEventType.DEVICE_ONLINE);
        assertThat(event.origin()).isEqualTo(LogOrigin.DEVICE_SIMULATION);
        assertThat(event.occurredAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW, SERVICE_ZONE));
        assertThat(event.payloadJson()).contains(
                "\"deviceCode\":\"TVM-ST001-01\"",
                "\"deviceType\":\"TICKET_MACHINE\"",
                "\"stationCode\":\"ST001\""
        );
    }

    @Test
    void shouldMoveAnErroredDeviceThroughMaintenanceBeforeReturningOnline() {
        DeviceEvent maintenanceStarted = generator.generate(device(
                "VAL-ST001-01",
                DeviceType.ENTRY_VALIDATOR,
                DeviceStatus.ERROR
        ));
        DeviceEvent maintenanceFinished = generator.generate(device(
                "VAL-ST001-02",
                DeviceType.EXIT_VALIDATOR,
                DeviceStatus.MAINTENANCE
        ));

        assertThat(maintenanceStarted.type())
                .isEqualTo(DeviceEventType.DEVICE_MAINTENANCE_STARTED);
        assertThat(maintenanceFinished.type())
                .isEqualTo(DeviceEventType.DEVICE_MAINTENANCE_FINISHED);
    }

    @Test
    void shouldOnlyGenerateEventsCompatibleWithTheDeviceType() {
        DeviceEvent ticketMachineEvent = generator.generate(device(
                "TVM-ST001-01",
                DeviceType.TICKET_MACHINE,
                DeviceStatus.ONLINE
        ));
        DeviceEvent validatorEvent = generator.generate(device(
                "VAL-ST001-01",
                DeviceType.ENTRY_VALIDATOR,
                DeviceStatus.ONLINE
        ));

        assertThat(ticketMachineEvent.type()).isIn(
                DeviceEventType.TICKET_PURCHASE_REQUESTED,
                DeviceEventType.TICKET_PURCHASE_COMPLETED,
                DeviceEventType.TICKET_PURCHASE_FAILED
        );
        assertThat(validatorEvent.type()).isIn(
                DeviceEventType.VALIDATION_ACCEPTED,
                DeviceEventType.VALIDATION_REJECTED,
                DeviceEventType.VALIDATION_FAILED
        );
        assertThat(Set.of(ticketMachineEvent.deviceCode(), validatorEvent.deviceCode()))
                .hasSize(2);
    }

    private Device device(String code, DeviceType type, DeviceStatus status) {
        Station station = mock(Station.class);
        when(station.getCode()).thenReturn("ST001");

        Device device = mock(Device.class);
        when(device.getCode()).thenReturn(code);
        when(device.getName()).thenReturn("Máquina " + code);
        when(device.getType()).thenReturn(type);
        when(device.getStatus()).thenReturn(status);
        when(device.getStation()).thenReturn(station);
        return device;
    }
}
