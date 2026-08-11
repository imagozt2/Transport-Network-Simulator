package com.transport.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.CompensatoryTicketIssuance;
import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.DeviceMqttCommand;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.CompensatoryIssuanceStatus;
import com.transport.simulator.enums.DeviceMqttCommandStatus;
import com.transport.simulator.enums.DeviceMqttCommandType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.repository.CompensatoryTicketIssuanceRepository;
import com.transport.simulator.repository.DeviceMqttCommandRepository;
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
import tools.jackson.databind.ObjectMapper;

class CompensatoryIssuanceAcknowledgementIntegrationTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T10:02:00Z"), ZoneOffset.UTC
    );

    @Test
    void shouldCompleteTheIssuanceWhenTheTicketMachineConfirmsPresentation() {
        Device device = mock(Device.class);
        when(device.getId()).thenReturn(41L);
        when(device.getCode()).thenReturn("RMM-SALE-ST001-01");
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
                  "deviceCode": "RMM-SALE-ST001-01",
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
                41L, "RMM-SALE-ST001-01", DeviceType.TICKET_MACHINE,
                "ST001", "ticket-machine-01", "RMM-SALE-ST001-01"
        );

        consumerCaptor.getValue().accept(new AuthenticatedMqttMessage(
                machine,
                "rmm/v1/devices/RMM-SALE-ST001-01/acks",
                acknowledgement.getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(command.getStatus()).isEqualTo(DeviceMqttCommandStatus.COMPLETED);
        assertThat(issuance.getStatus()).isEqualTo(CompensatoryIssuanceStatus.COMPLETED);
        assertThat(issuance.getCompletedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(issuance.getIssuedTicket()).isSameAs(ticket);
        verify(eventService).registerCompleted(issuance, LocalDateTime.now(CLOCK));
    }
}
