package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.Station;
import com.transport.simulator.enums.DeviceConnectivityState;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.repository.DeviceEventLogRepository;
import com.transport.simulator.repository.DeviceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeviceOperationsQueryServiceTests {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime LAST_COMMUNICATION =
            LocalDateTime.of(2026, 8, 11, 12, 30);

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceEventLogRepository eventLogRepository;

    @Test
    void shouldExposeRealMqttConnectivityAndUnmonitoredDevicesSeparately() {
        Device connected = device(1L, "RMM-MB-ST001-001");
        connected.recordMqttCommunication(LAST_COMMUNICATION);
        Device unmonitored = device(2L, "RMM-MB-ST001-002");
        when(deviceRepository.findAllByActiveTrueOrderByCodeAsc())
                .thenReturn(List.of(connected, unmonitored));
        when(eventLogRepository.findLatestForEachDevice()).thenReturn(List.of());
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T10:31:00Z"), ZONE);
        DeviceOperationsQueryService service = new DeviceOperationsQueryService(
                deviceRepository, eventLogRepository, clock
        );

        var response = service.getOperations(null, null, null, null);

        assertThat(response.devices()).hasSize(2);
        var connectedResponse = response.devices().get(0);
        assertThat(connectedResponse.connectivity().state())
                .isEqualTo(DeviceConnectivityState.CONNECTED);
        assertThat(connectedResponse.connectivity().lastCommunicationAt())
                .isEqualTo(LAST_COMMUNICATION);
        assertThat(response.devices().get(1).connectivity().state())
                .isEqualTo(DeviceConnectivityState.NOT_MONITORED);
        assertThat(response.evaluatedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 11, 12, 31));
    }

    private Device device(Long id, String code) {
        Device device = BeanUtils.instantiateClass(Device.class);
        ReflectionTestUtils.setField(device, "id", id);
        ReflectionTestUtils.setField(device, "code", code);
        ReflectionTestUtils.setField(device, "name", "Máquina " + code);
        ReflectionTestUtils.setField(device, "station", new Station("ST001", "Central"));
        ReflectionTestUtils.setField(device, "status", DeviceStatus.ONLINE);
        ReflectionTestUtils.setField(device, "type", DeviceType.TICKET_MACHINE);
        ReflectionTestUtils.setField(device, "active", true);
        return device;
    }
}
