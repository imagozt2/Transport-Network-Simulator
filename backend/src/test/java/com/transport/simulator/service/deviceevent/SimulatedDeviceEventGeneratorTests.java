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
import com.transport.simulator.enums.LogSeverity;
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
    void shouldGenerateTheServiceOpeningEventExplicitly() {
        DeviceEvent event = generator.generateServiceState(device(
                "TVM-ST001-01",
                DeviceType.TICKET_MACHINE,
                DeviceStatus.OFFLINE
        ), true);

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
    void shouldGenerateTheServiceClosingEventExplicitly() {
        DeviceEvent event = generator.generateServiceState(device(
                "VAL-ST001-01",
                DeviceType.ENTRY_VALIDATOR,
                DeviceStatus.ONLINE
        ), false);

        assertThat(event.type()).isEqualTo(DeviceEventType.DEVICE_OFFLINE);
        assertThat(event.severity()).isEqualTo(LogSeverity.INFO);
    }

    @Test
    void shouldOnlyGenerateNonErrorEventsCompatibleWithTheDeviceType() {
        Device ticketMachine = device(
                "TVM-ST001-01",
                DeviceType.TICKET_MACHINE,
                DeviceStatus.ONLINE
        );
        Device validator = device(
                "VAL-ST001-01",
                DeviceType.ENTRY_VALIDATOR,
                DeviceStatus.ONLINE
        );

        Set<DeviceEventType> ticketEvents = java.util.stream.IntStream.range(0, 200)
                .mapToObj(ignored -> generator.generateOperationalActivity(ticketMachine))
                .peek(event -> assertThat(event.severity()).isNotIn(
                        LogSeverity.ERROR,
                        LogSeverity.CRITICAL
                ))
                .map(DeviceEvent::type)
                .collect(java.util.stream.Collectors.toSet());
        Set<DeviceEventType> validatorEvents = java.util.stream.IntStream.range(0, 200)
                .mapToObj(ignored -> generator.generateOperationalActivity(validator))
                .peek(event -> assertThat(event.severity()).isNotIn(
                        LogSeverity.ERROR,
                        LogSeverity.CRITICAL
                ))
                .map(DeviceEvent::type)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(ticketEvents).doesNotContain(DeviceEventType.TICKET_PURCHASE_FAILED);
        assertThat(validatorEvents).doesNotContain(DeviceEventType.VALIDATION_FAILED);
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
