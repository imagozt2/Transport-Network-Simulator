package com.transport.simulator.mqtt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.enums.DeviceMqttCommandStatus;
import com.transport.simulator.repository.DeviceMqttCommandRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

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
}
