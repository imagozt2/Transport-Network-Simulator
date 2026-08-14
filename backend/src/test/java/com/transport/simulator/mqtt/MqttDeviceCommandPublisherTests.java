package com.transport.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MqttDeviceCommandPublisherTests {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Europe/Madrid");
    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");

    @Mock private DeviceMqttCommandRepository commandRepository;
    @Mock private DeviceMqttIdentityRepository identityRepository;
    @Mock private ControlCenterMqttClient mqttClient;

    private MqttDeviceCommandPublisher publisher;
    private DeviceMqttCommand command;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, SERVICE_ZONE);
        Device device = mock(Device.class);
        when(device.getId()).thenReturn(21L);
        when(device.getCode()).thenReturn("TVM-ST001-01");
        LocalDateTime requestedAt = LocalDateTime.now(clock).minusSeconds(5);
        command = new DeviceMqttCommand(
                "cmd-001", "8ef4e572-1a2c-4aa9-957a-8bf683272f64", device,
                DeviceMqttCommandType.STATUS_REQUEST, "{}", requestedAt,
                requestedAt.plusMinutes(2));
        publisher = new MqttDeviceCommandPublisher(commandRepository, identityRepository,
                mqttClient, new ObjectMapper(), clock);
    }

    @Test
    void shouldPublishACommandWithItsStableIdentifiers() {
        DeviceMqttIdentity identity = mock(DeviceMqttIdentity.class);
        when(commandRepository.findByIdForPublication(7L)).thenReturn(Optional.of(command));
        when(identityRepository.findByDeviceId(21L)).thenReturn(Optional.of(identity));
        when(identity.canAuthenticate(LocalDateTime.ofInstant(NOW, SERVICE_ZONE))).thenReturn(true);
        when(identity.getMqttClientId()).thenReturn("TVM-ST001-01");
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

        publisher.republish(7L);

        verify(mqttClient).publish(eq("rmm/v1/devices/TVM-ST001-01/commands"),
                payload.capture(), eq(1), eq(false));
        assertThat(payload.getValue())
                .contains("\"commandId\":\"cmd-001\"")
                .contains("\"messageId\":\"8ef4e572-1a2c-4aa9-957a-8bf683272f64\"")
                .contains("\"type\":\"device.status-request-command\"");
        assertThat(command.getStatus()).isEqualTo(DeviceMqttCommandStatus.PUBLISHED);
        assertThat(command.getPublicationAttempts()).isEqualTo(1);
    }

    @Test
    void shouldKeepTheCommandRecoverableWhenTransportIsUnavailable() {
        DeviceMqttIdentity identity = mock(DeviceMqttIdentity.class);
        when(commandRepository.findByIdForPublication(7L)).thenReturn(Optional.of(command));
        when(identityRepository.findByDeviceId(21L)).thenReturn(Optional.of(identity));
        when(identity.canAuthenticate(LocalDateTime.ofInstant(NOW, SERVICE_ZONE))).thenReturn(true);
        when(identity.getMqttClientId()).thenReturn("TVM-ST001-01");
        org.mockito.Mockito.doThrow(new MqttTransportException("Broker unavailable"))
                .when(mqttClient).publish(eq("rmm/v1/devices/TVM-ST001-01/commands"),
                        org.mockito.ArgumentMatchers.anyString(), eq(1), eq(false));

        publisher.republish(7L);

        assertThat(command.getStatus()).isEqualTo(DeviceMqttCommandStatus.PUBLISH_FAILED);
        assertThat(command.getPublicationAttempts()).isEqualTo(1);
        assertThat(command.getLastPublicationError()).isEqualTo("Broker unavailable");
    }

    @Test
    void shouldCorrelateATicketResponseWithItsPurchase() throws Exception {
        Device device = command.getDevice();
        LocalDateTime requestedAt = LocalDateTime.ofInstant(NOW, SERVICE_ZONE).minusSeconds(5);
        String purchaseReference = "9561ad31-6273-42d9-b76f-2dabb0b60955";
        DeviceMqttCommand ticketCommand = new DeviceMqttCommand(
                "cmd-ticket", "344de998-f2da-45d2-be7f-ec3e63457333", device,
                DeviceMqttCommandType.TICKET_ISSUE,
                new ObjectMapper().writeValueAsString(Map.of(
                        "issuanceKind", "PURCHASE",
                        "purchaseReference", purchaseReference,
                        "issuanceCode", "RMM-PUR-001",
                        "ticket", Map.of("ticketCode", "RMM-TICKET-001")
                )), requestedAt, requestedAt.plusMinutes(2));
        DeviceMqttIdentity identity = mock(DeviceMqttIdentity.class);
        when(commandRepository.findByIdForPublication(8L)).thenReturn(Optional.of(ticketCommand));
        when(identityRepository.findByDeviceId(21L)).thenReturn(Optional.of(identity));
        when(identity.canAuthenticate(LocalDateTime.ofInstant(NOW, SERVICE_ZONE))).thenReturn(true);
        when(identity.getMqttClientId()).thenReturn("TVM-ST001-01");
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

        publisher.republish(8L);

        verify(mqttClient).publish(eq("rmm/v1/devices/TVM-ST001-01/commands"),
                payload.capture(), eq(1), eq(false));
        assertThat(payload.getValue())
                .contains("\"correlationId\":\"" + purchaseReference + "\"")
                .contains("\"issuanceKind\":\"PURCHASE\"")
                .contains("\"purchaseReference\":\"" + purchaseReference + "\"");
    }
}
