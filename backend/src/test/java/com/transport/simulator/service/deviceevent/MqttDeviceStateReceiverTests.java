package com.transport.simulator.service.deviceevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.enums.DeviceMqttPresence;
import com.transport.simulator.enums.DeviceEventSource;
import com.transport.simulator.enums.DeviceOperationalState;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.mqtt.AuthenticatedMqttMachine;
import com.transport.simulator.mqtt.AuthenticatedMqttMessage;
import com.transport.simulator.mqtt.AuthenticatedMqttMessageRouter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MqttDeviceStateReceiverTests {
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-13T10:15:30Z");
    private static final AuthenticatedMqttMachine MACHINE = new AuthenticatedMqttMachine(
            46L, "RMM-TM-ST046-01", DeviceType.TICKET_MACHINE,
            "ST046", "instance-st046-01", "RMM-TM-ST046-01");

    @Mock private AuthenticatedMqttMessageRouter router;
    @Mock private MqttDeviceStateService stateService;
    @Mock private DeviceEventRegistrationService eventRegistrationService;

    private Consumer<AuthenticatedMqttMessage> receiver;

    @BeforeEach
    void setUp() {
        ArgumentCaptor<Consumer<AuthenticatedMqttMessage>> captor = consumerCaptor();
        new MqttDeviceStateReceiver(router, stateService, eventRegistrationService,
                new ObjectMapper(),
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC));
        verify(router).register(captor.capture());
        receiver = captor.getValue();
    }

    @Test
    void shouldPreserveUtf8StatusValuesAndTheAuthenticatedMachineIdentity() {
        String payload = """
                {
                  "schemaVersion": 1,
                  "type": "device.status-reported",
                  "deviceCode": "RMM-TM-ST046-01",
                  "occurredAt": "2026-08-13T10:14:00Z",
                  "payload": {
                    "operationalState": "AVAILABLE",
                    "serviceMode": "Operación pública · El Espigón",
                    "softwareVersion": "versión-1.0",
                    "uptimeSeconds": 125
                  }
                }
                """;

        receive("rmm/v1/devices/RMM-TM-ST046-01/status", payload);

        verify(stateService).updateOperationalState(
                MACHINE, DeviceOperationalState.AVAILABLE,
                "Operación pública · El Espigón", "versión-1.0", 125L,
                LocalDateTime.of(2026, 8, 13, 10, 14));
    }

    @Test
    void shouldUseBackendReceptionTimeForTheAuthenticatedMachinePresence() {
        String payload = """
                {
                  "schemaVersion": 1,
                  "state": "ONLINE",
                  "reason": "Conexión MQTT establecida",
                  "changedAt": "2020-01-01T00:00:00Z"
                }
                """;

        LocalDateTime receivedAt = LocalDateTime.ofInstant(RECEIVED_AT, ZoneOffset.UTC);
        when(stateService.updatePresence(MACHINE, DeviceMqttPresence.ONLINE, receivedAt))
                .thenReturn(true);

        receive("rmm/v1/devices/RMM-TM-ST046-01/presence", payload);

        verify(stateService).updatePresence(MACHINE, DeviceMqttPresence.ONLINE,
                receivedAt);
        ArgumentCaptor<DeviceEvent> eventCaptor = ArgumentCaptor.forClass(DeviceEvent.class);
        verify(eventRegistrationService).register(eventCaptor.capture());
        assertThat(eventCaptor.getValue().origin()).isEqualTo(LogOrigin.MQTT);
        assertThat(eventCaptor.getValue().source()).isEqualTo(DeviceEventSource.REAL);
    }

    private void receive(String topic, String payload) {
        receiver.accept(new AuthenticatedMqttMessage(
                MACHINE, topic, payload.getBytes(StandardCharsets.UTF_8)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Consumer<AuthenticatedMqttMessage>> consumerCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Consumer.class);
    }
}
