package com.transport.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.CompensatoryTicketIssuance;
import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.DeviceMqttCommand;
import com.transport.simulator.entity.DeviceMqttIdentity;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.CompensatoryIssuanceStatus;
import com.transport.simulator.enums.DeviceMqttCommandStatus;
import com.transport.simulator.enums.DeviceMqttCommandType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.repository.CompensatoryTicketIssuanceRepository;
import com.transport.simulator.repository.DeviceMqttCommandRepository;
import com.transport.simulator.repository.DeviceMqttIdentityRepository;
import com.transport.simulator.service.TicketIssuanceEventRegistrationService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

class CompensatoryIssuanceAcknowledgementIntegrationTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T10:02:00Z"), ZoneOffset.UTC
    );

    @Test
    void shouldCompleteTheIssuanceWhenTheTicketMachineConfirmsPresentation() {
        Device device = mock(Device.class);
        when(device.getId()).thenReturn(41L);
        when(device.getCode()).thenReturn("RMM-TM-ST001-01");
        TicketProduct product = mock(TicketProduct.class);
        OperatorAccount operator = mock(OperatorAccount.class);
        Ticket ticket = mock(Ticket.class);

        LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 11, 10, 0);
        CompensatoryTicketIssuance issuance = new CompensatoryTicketIssuance(
                "COMP-INTEGRATION-001", product, device, operator,
                "La compra se completó sin imprimir el billete", requestedAt
        );
        issuance.beginProcessing(ticket);
        DeviceMqttCommand command = new DeviceMqttCommand(
                "command-compensatory-001",
                "76b99aa0-72fd-4e9b-aa35-f8f4491961e6",
                device,
                DeviceMqttCommandType.TICKET_ISSUE,
                "{\"issuanceCode\":\"COMP-INTEGRATION-001\"}",
                requestedAt,
                requestedAt.plusMinutes(5)
        );
        command.markPublished(requestedAt.plusSeconds(1));

        DeviceMqttCommandRepository commandRepository = mock(DeviceMqttCommandRepository.class);
        CompensatoryTicketIssuanceRepository issuanceRepository =
                mock(CompensatoryTicketIssuanceRepository.class);
        TicketIssuanceEventRegistrationService eventService =
                mock(TicketIssuanceEventRegistrationService.class);
        when(commandRepository.findByCommandIdForAcknowledgement("command-compensatory-001"))
                .thenReturn(Optional.of(command));
        when(issuanceRepository.findByCodeForUpdate("COMP-INTEGRATION-001"))
                .thenReturn(Optional.of(issuance));

        MqttDeviceCommandAcknowledgementService acknowledgementService =
                new MqttDeviceCommandAcknowledgementService(
                        commandRepository, issuanceRepository, eventService, CLOCK
                );
        AuthenticatedMqttMessageRouter router = mock(AuthenticatedMqttMessageRouter.class);
        new MqttDeviceCommandAcknowledgementReceiver(
                router, acknowledgementService, new ObjectMapper()
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<AuthenticatedMqttMessage>> consumerCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(router).register(consumerCaptor.capture());

        String acknowledgement = """
                {
                  "schemaVersion": 1,
                  "messageId": "cc7096fc-f4e0-4368-b237-178396a9ccfb",
                  "correlationId": "command-compensatory-001",
                  "type": "ticket.issue-acknowledged",
                  "deviceCode": "RMM-TM-ST001-01",
                  "occurredAt": "2026-08-11T10:02:00.000Z",
                  "sentAt": "2026-08-11T10:02:00.000Z",
                  "payload": {
                    "commandId": "command-compensatory-001",
                    "issuanceCode": "COMP-INTEGRATION-001",
                    "status": "COMPLETED",
                    "resultCode": "TICKET_PRESENTED",
                    "completedAt": "2026-08-11T10:02:00.000Z"
                  }
                }
                """;
        AuthenticatedMqttMachine machine = new AuthenticatedMqttMachine(
                41L, "RMM-TM-ST001-01", DeviceType.TICKET_MACHINE,
                "ST001", "ticket-machine-01", "RMM-TM-ST001-01"
        );

        consumerCaptor.getValue().accept(new AuthenticatedMqttMessage(
                machine,
                "rmm/v1/devices/RMM-TM-ST001-01/acks",
                acknowledgement.getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(command.getStatus()).isEqualTo(DeviceMqttCommandStatus.COMPLETED);
        assertThat(issuance.getStatus()).isEqualTo(CompensatoryIssuanceStatus.COMPLETED);
        assertThat(issuance.getCompletedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(issuance.getIssuedTicket()).isSameAs(ticket);
        verify(eventService).registerCompleted(issuance, LocalDateTime.now(CLOCK));
    }

    @Test
    void shouldRecoverAPendingIssuanceAndIgnoreItsDuplicatedAcknowledgement() {
        Device device = mock(Device.class);
        when(device.getId()).thenReturn(41L);
        when(device.getCode()).thenReturn("RMM-TM-ST001-01");
        TicketProduct product = mock(TicketProduct.class);
        OperatorAccount operator = mock(OperatorAccount.class);
        Ticket ticket = mock(Ticket.class);
        LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 11, 10, 0);
        CompensatoryTicketIssuance issuance = new CompensatoryTicketIssuance(
                "COMP-RECOVERY-001", product, device, operator,
                "Reemisión pendiente durante una desconexión", requestedAt);
        issuance.beginProcessing(ticket);
        DeviceMqttCommand command = new DeviceMqttCommand(
                "command-recovery-001", "e1de47d9-40aa-49af-a515-87c8fe068fd1",
                device, DeviceMqttCommandType.TICKET_ISSUE,
                "{\"issuanceCode\":\"COMP-RECOVERY-001\"}", requestedAt,
                requestedAt.plusMinutes(5));

        DeviceMqttCommandRepository commandRepository = mock(DeviceMqttCommandRepository.class);
        DeviceMqttIdentityRepository identityRepository = mock(DeviceMqttIdentityRepository.class);
        DeviceMqttIdentity identity = mock(DeviceMqttIdentity.class);
        ControlCenterMqttClient mqttClient = mock(ControlCenterMqttClient.class);
        when(commandRepository.findByIdForPublication(7L)).thenReturn(Optional.of(command));
        when(identityRepository.findByDeviceId(41L)).thenReturn(Optional.of(identity));
        when(identity.canAuthenticate(LocalDateTime.now(CLOCK))).thenReturn(true);
        when(identity.getMqttClientId()).thenReturn("RMM-TM-ST001-01");
        doThrow(new MqttTransportException("Broker unavailable"))
                .doNothing()
                .when(mqttClient).publish(
                        eq("rmm/v1/devices/RMM-TM-ST001-01/commands"),
                        anyString(), eq(1), eq(false));
        MqttDeviceCommandPublisher publisher = new MqttDeviceCommandPublisher(
                commandRepository, identityRepository, mqttClient,
                new ObjectMapper(), CLOCK);

        publisher.republish(7L);

        assertThat(command.getStatus()).isEqualTo(DeviceMqttCommandStatus.PUBLISH_FAILED);
        when(mqttClient.connection()).thenReturn(new MqttConnectionSnapshot(
                MqttConnectionState.CONNECTED, "rmm-backend",
                "tcp://localhost:1883", CLOCK.instant(), null));
        when(commandRepository.findRecoverableCommandIds(
                eq(java.util.List.of(DeviceMqttCommandStatus.PENDING,
                        DeviceMqttCommandStatus.PUBLISH_FAILED)),
                eq(LocalDateTime.now(CLOCK)), eq(5), any(Pageable.class)))
                .thenReturn(java.util.List.of(7L));
        new MqttPendingCommandRecovery(
                commandRepository, publisher, mqttClient, CLOCK, 5, 20)
                .onConnected(new MqttConnectedEvent(
                        true, "tcp://localhost:1883", CLOCK.instant()));

        assertThat(command.getStatus()).isEqualTo(DeviceMqttCommandStatus.PUBLISHED);
        assertThat(command.getPublicationAttempts()).isEqualTo(2);

        CompensatoryTicketIssuanceRepository issuanceRepository =
                mock(CompensatoryTicketIssuanceRepository.class);
        TicketIssuanceEventRegistrationService eventService =
                mock(TicketIssuanceEventRegistrationService.class);
        when(commandRepository.findByCommandIdForAcknowledgement("command-recovery-001"))
                .thenReturn(Optional.of(command));
        when(issuanceRepository.findByCodeForUpdate("COMP-RECOVERY-001"))
                .thenReturn(Optional.of(issuance));
        MqttDeviceCommandAcknowledgementService acknowledgementService =
                new MqttDeviceCommandAcknowledgementService(
                        commandRepository, issuanceRepository, eventService, CLOCK);

        acknowledgementService.acknowledge(
                41L, "command-recovery-001", "COMP-RECOVERY-001",
                DeviceMqttCommandStatus.COMPLETED, "TICKET_PRESENTED");
        acknowledgementService.acknowledge(
                41L, "command-recovery-001", "COMP-RECOVERY-001",
                DeviceMqttCommandStatus.COMPLETED, "TICKET_PRESENTED");

        assertThat(command.getStatus()).isEqualTo(DeviceMqttCommandStatus.COMPLETED);
        assertThat(issuance.getStatus()).isEqualTo(CompensatoryIssuanceStatus.COMPLETED);
        verify(eventService, times(1)).registerCompleted(issuance, LocalDateTime.now(CLOCK));
        verify(mqttClient, times(2)).publish(
                eq("rmm/v1/devices/RMM-TM-ST001-01/commands"),
                anyString(), eq(1), eq(false));
    }

    @Test
    void shouldFailTheIssuanceWhenTheTicketMachineRejectsPresentation() {
        Device device = mock(Device.class);
        when(device.getId()).thenReturn(41L);
        TicketProduct product = mock(TicketProduct.class);
        OperatorAccount operator = mock(OperatorAccount.class);
        Ticket ticket = mock(Ticket.class);
        LocalDateTime requestedAt = LocalDateTime.of(2026, 8, 11, 10, 0);
        CompensatoryTicketIssuance issuance = new CompensatoryTicketIssuance(
                "COMP-INTEGRATION-FAILED", product, device, operator,
                "La máquina no pudo presentar el billete", requestedAt
        );
        issuance.beginProcessing(ticket);
        DeviceMqttCommand command = new DeviceMqttCommand(
                "command-compensatory-failed", "request-failed", device,
                DeviceMqttCommandType.TICKET_ISSUE, "{}", requestedAt, requestedAt.plusMinutes(5)
        );
        command.markPublished(requestedAt.plusSeconds(1));
        DeviceMqttCommandRepository commandRepository = mock(DeviceMqttCommandRepository.class);
        CompensatoryTicketIssuanceRepository issuanceRepository =
                mock(CompensatoryTicketIssuanceRepository.class);
        TicketIssuanceEventRegistrationService eventService =
                mock(TicketIssuanceEventRegistrationService.class);
        when(commandRepository.findByCommandIdForAcknowledgement("command-compensatory-failed"))
                .thenReturn(Optional.of(command));
        when(issuanceRepository.findByCodeForUpdate("COMP-INTEGRATION-FAILED"))
                .thenReturn(Optional.of(issuance));
        MqttDeviceCommandAcknowledgementService service =
                new MqttDeviceCommandAcknowledgementService(
                        commandRepository, issuanceRepository, eventService, CLOCK);

        service.acknowledge(
                41L, "command-compensatory-failed", "COMP-INTEGRATION-FAILED",
                DeviceMqttCommandStatus.FAILED, "PRINTER_UNAVAILABLE"
        );

        assertThat(command.getStatus()).isEqualTo(DeviceMqttCommandStatus.FAILED);
        assertThat(issuance.getStatus()).isEqualTo(CompensatoryIssuanceStatus.FAILED);
        verify(eventService).registerFailed(issuance, LocalDateTime.now(CLOCK));
    }
}
