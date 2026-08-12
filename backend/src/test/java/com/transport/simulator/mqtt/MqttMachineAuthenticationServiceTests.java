package com.transport.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.DeviceMqttIdentity;
import com.transport.simulator.entity.Station;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.repository.DeviceMqttIdentityRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MqttMachineAuthenticationServiceTests {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-12T10:00:00Z"), ZoneOffset.UTC);

    private final DeviceMqttIdentityRepository repository =
            mock(DeviceMqttIdentityRepository.class);
    private final MqttMachineAuthenticationService service =
            new MqttMachineAuthenticationService(repository, CLOCK);

    @Test
    void shouldAuthenticateEveryCanonicalMachineType() {
        assertThat(authenticate("RMM-TM-ST001-01", DeviceType.TICKET_MACHINE).deviceType())
                .isEqualTo(DeviceType.TICKET_MACHINE);
        assertThat(authenticate("RMM-EN-ST001-01", DeviceType.ENTRY_VALIDATOR).deviceType())
                .isEqualTo(DeviceType.ENTRY_VALIDATOR);
        assertThat(authenticate("RMM-EX-ST001-01", DeviceType.EXIT_VALIDATOR).deviceType())
                .isEqualTo(DeviceType.EXIT_VALIDATOR);
    }

    @Test
    void shouldRejectAnIdentityWhosePrefixDoesNotMatchTheInventoryType() {
        DeviceMqttIdentity identity = identity(
                "RMM-TM-ST001-01", DeviceType.ENTRY_VALIDATOR);
        when(repository.findByClientIdForAuthentication("RMM-TM-ST001-01"))
                .thenReturn(Optional.of(identity));

        assertThatThrownBy(() -> service.authenticate(
                "rmm/v1/devices/RMM-TM-ST001-01/events", "RMM-TM-ST001-01"))
                .isInstanceOf(MqttMachineAuthenticationException.class)
                .hasMessageContaining("incompatible with its device type");
    }

    private AuthenticatedMqttMachine authenticate(String deviceCode, DeviceType deviceType) {
        DeviceMqttIdentity identity = identity(deviceCode, deviceType);
        when(repository.findByClientIdForAuthentication(deviceCode))
                .thenReturn(Optional.of(identity));

        AuthenticatedMqttMachine authenticated = service.authenticate(
                "rmm/v1/devices/" + deviceCode + "/events", deviceCode);

        verify(identity).recordAuthentication(LocalDateTime.now(CLOCK));
        assertThat(authenticated.deviceCode()).isEqualTo(deviceCode);
        return authenticated;
    }

    private DeviceMqttIdentity identity(String deviceCode, DeviceType deviceType) {
        Station station = mock(Station.class);
        when(station.getCode()).thenReturn("ST001");
        Device device = mock(Device.class);
        when(device.getId()).thenReturn(41L);
        when(device.getCode()).thenReturn(deviceCode);
        when(device.getType()).thenReturn(deviceType);
        when(device.getStation()).thenReturn(station);
        DeviceMqttIdentity identity = mock(DeviceMqttIdentity.class);
        when(identity.getDevice()).thenReturn(device);
        when(identity.canAuthenticate(LocalDateTime.now(CLOCK))).thenReturn(true);
        when(identity.getInstanceId()).thenReturn("machine-instance-01");
        when(identity.getMqttClientId()).thenReturn(deviceCode);
        return identity;
    }
}
