package com.transport.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.DeviceMqttCommand;
import com.transport.simulator.entity.DeviceMqttIdentity;
import com.transport.simulator.enums.DeviceMqttCommandStatus;
import com.transport.simulator.enums.DeviceMqttCommandType;
import com.transport.simulator.repository.DeviceMqttCommandRepository;
import com.transport.simulator.repository.DeviceMqttIdentityRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MqttConnectionRecoveryTests {
    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Europe/Madrid");

    @Mock private DeviceMqttCommandRepository commandRepository;
    @Mock private MqttDeviceCommandPublisher commandPublisher;
    @Mock private ControlCenterMqttClient mqttClient;
    private MqttPendingCommandRecovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new MqttPendingCommandRecovery(commandRepository, commandPublisher,
                mqttClient, Clock.fixed(NOW, SERVICE_ZONE), 5, 20);
    }

    @Test
    void shouldRetryPendingAndFailedCommandsAfterReconnection() {
        when(mqttClient.connection()).thenReturn(new MqttConnectionSnapshot(
                MqttConnectionState.CONNECTED, "rmm-backend", "tcp://localhost:1883", NOW, null));
        when(commandRepository.findRecoverableCommandIds(
                eq(List.of(DeviceMqttCommandStatus.PENDING, DeviceMqttCommandStatus.PUBLISH_FAILED)),
                eq(LocalDateTime.ofInstant(NOW, SERVICE_ZONE)), eq(5), any(Pageable.class)))
                .thenReturn(List.of(4L, 9L));

        recovery.onConnected(new MqttConnectedEvent(true, "tcp://localhost:1883", NOW));

        verify(commandPublisher).republish(4L);
        verify(commandPublisher).republish(9L);
    }

    @Test
    void shouldNotConsumePublicationAttemptsWhileDisconnected() {
        when(mqttClient.connection()).thenReturn(new MqttConnectionSnapshot(
                MqttConnectionState.DISCONNECTED, "rmm-backend", "tcp://localhost:1883",
                NOW, "Connection lost"));

        recovery.retryPendingCommands();

        verify(commandRepository, never()).findRecoverableCommandIds(
                any(), any(), eq(5), any(Pageable.class));
        verify(commandPublisher, never()).republish(any());
    }

    @Test
    void shouldAskTheClientToReconnectWhenTheSupervisorRuns() {
        new MqttConnectionSupervisor(mqttClient).reconnectIfNecessary();

        verify(mqttClient).connectIfNecessary();
    }

    @Test
    void shouldPublishACommandAfterTheBrokerConnectionIsRecovered() {
        Clock clock = Clock.fixed(NOW, SERVICE_ZONE);
        Device device = mock(Device.class);
        DeviceMqttIdentity identity = mock(DeviceMqttIdentity.class);
        DeviceMqttIdentityRepository identityRepository = mock(DeviceMqttIdentityRepository.class);
        when(device.getId()).thenReturn(21L);
        when(device.getCode()).thenReturn("TVM-ST001-01");
        LocalDateTime requestedAt = LocalDateTime.now(clock).minusSeconds(5);
        DeviceMqttCommand pendingCommand = new DeviceMqttCommand(
                "cmd-recovery-001", "1ca801d0-aa46-44fc-80f8-94774c93e5ed", device,
                DeviceMqttCommandType.STATUS_REQUEST, "{}", requestedAt,
                requestedAt.plusMinutes(2));
        when(commandRepository.findByIdForPublication(7L))
                .thenReturn(Optional.of(pendingCommand));
        when(identityRepository.findByDeviceId(21L)).thenReturn(Optional.of(identity));
        when(identity.canAuthenticate(LocalDateTime.ofInstant(NOW, SERVICE_ZONE))).thenReturn(true);
        when(identity.getMqttClientId()).thenReturn("TVM-ST001-01");
        doThrow(new MqttTransportException("Broker unavailable"))
                .doNothing()
                .when(mqttClient).publish(eq("rmm/v1/devices/TVM-ST001-01/commands"),
                        anyString(), eq(1), eq(false));
        MqttDeviceCommandPublisher realPublisher = new MqttDeviceCommandPublisher(
                commandRepository, identityRepository, mqttClient, new ObjectMapper(), clock);

        realPublisher.republish(7L);

        assertThat(pendingCommand.getStatus()).isEqualTo(DeviceMqttCommandStatus.PUBLISH_FAILED);
        when(mqttClient.connection()).thenReturn(new MqttConnectionSnapshot(
                MqttConnectionState.CONNECTED, "rmm-backend", "tcp://localhost:1883", NOW, null));
        when(commandRepository.findRecoverableCommandIds(
                eq(List.of(DeviceMqttCommandStatus.PENDING, DeviceMqttCommandStatus.PUBLISH_FAILED)),
                eq(LocalDateTime.ofInstant(NOW, SERVICE_ZONE)), eq(5), any(Pageable.class)))
                .thenReturn(List.of(7L));
        MqttPendingCommandRecovery integratedRecovery = new MqttPendingCommandRecovery(
                commandRepository, realPublisher, mqttClient, clock, 5, 20);

        integratedRecovery.onConnected(new MqttConnectedEvent(true, "tcp://localhost:1883", NOW));

        assertThat(pendingCommand.getStatus()).isEqualTo(DeviceMqttCommandStatus.PUBLISHED);
        assertThat(pendingCommand.getPublicationAttempts()).isEqualTo(2);
        assertThat(pendingCommand.getLastPublicationError()).isNull();
        verify(mqttClient, times(2)).publish(eq("rmm/v1/devices/TVM-ST001-01/commands"),
                anyString(), eq(1), eq(false));
    }
}
