package com.transport.simulator.service.deviceevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.TicketQrValidationType;
import com.transport.simulator.enums.TicketValidationStatus;
import com.transport.simulator.mqtt.AuthenticatedMqttMachine;
import com.transport.simulator.mqtt.AuthenticatedMqttMessage;
import com.transport.simulator.mqtt.AuthenticatedMqttMessageRouter;
import com.transport.simulator.mqtt.MqttTicketRechargeResponsePublisher;
import com.transport.simulator.mqtt.MqttTicketValidationResponsePublisher;
import com.transport.simulator.service.TicketMachinePurchaseService;
import com.transport.simulator.service.TicketMachineRechargeService;
import com.transport.simulator.service.TicketValidationService;
import com.transport.simulator.service.model.TicketValidationDecision;
import com.transport.simulator.service.model.TicketValidationRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ValidatorEntryExitMqttIntegrationTests {

    @Test
    void shouldProcessEntryAndExitFromTheirAuthenticatedValidators() {
        AuthenticatedMqttMessageRouter router = mock(AuthenticatedMqttMessageRouter.class);
        DeviceEventIngress ingress = mock(DeviceEventIngress.class);
        TicketValidationService validationService = mock(TicketValidationService.class);
        MqttTicketValidationResponsePublisher publisher =
                mock(MqttTicketValidationResponsePublisher.class);
        when(ingress.receive(any())).thenReturn(new DeviceEventReceipt(
                "event-message", 101L, DeviceEventReceipt.Status.ACCEPTED));

        TicketValidationDecision entryDecision = decision(
                "276b0e6c-d583-4f64-b304-eabbf63d6aac", "ST001", 9);
        TicketValidationDecision exitDecision = decision(
                "f4adeab9-2d24-4d67-b7ce-a77027302adf", "ST010", 9);
        when(validationService.validate(eq(51L), any())).thenReturn(entryDecision);
        when(validationService.validate(eq(52L), any())).thenReturn(exitDecision);

        new MqttTicketOperationEventReceiver(
                router, ingress, new ObjectMapper(),
                mock(TicketMachinePurchaseService.class), validationService, publisher,
                mock(TicketMachineRechargeService.class),
                mock(MqttTicketRechargeResponsePublisher.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<AuthenticatedMqttMessage>> consumerCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(router).register(consumerCaptor.capture());
        Consumer<AuthenticatedMqttMessage> receiver = consumerCaptor.getValue();

        AuthenticatedMqttMachine entryMachine = machine(
                51L, "RMM-EN-ST001-01", DeviceType.ENTRY_VALIDATOR, "ST001");
        AuthenticatedMqttMachine exitMachine = machine(
                52L, "RMM-EX-ST010-01", DeviceType.EXIT_VALIDATOR, "ST010");

        receiver.accept(validationMessage(
                entryMachine, "8ab2dd26-1cda-4f82-a989-ea09fa6d91b9",
                entryDecision.validationReference(), "ENTRY", "ST001"));
        receiver.accept(validationMessage(
                exitMachine, "a2d1bf25-e9a1-46e5-9044-f324caa2a4b4",
                exitDecision.validationReference(), "EXIT", "ST010"));

        ArgumentCaptor<TicketValidationRequest> requestCaptor =
                ArgumentCaptor.forClass(TicketValidationRequest.class);
        verify(validationService).validate(eq(51L), requestCaptor.capture());
        verify(validationService).validate(eq(52L), requestCaptor.capture());
        List<TicketValidationRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).extracting(TicketValidationRequest::direction)
                .containsExactly(TicketQrValidationType.ENTRY, TicketQrValidationType.EXIT);
        assertThat(requests).extracting(TicketValidationRequest::stationCode)
                .containsExactly("ST001", "ST010");
        assertThat(requests).extracting(TicketValidationRequest::qrValue)
                .containsOnly("RMM:TICKET:1:signed");

        ArgumentCaptor<DeviceEventMessage> eventCaptor =
                ArgumentCaptor.forClass(DeviceEventMessage.class);
        verify(ingress, times(2)).receive(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).extracting(DeviceEventMessage::type)
                .containsExactly(DeviceEventType.VALIDATION_REQUESTED,
                        DeviceEventType.VALIDATION_REQUESTED);
        assertThat(eventCaptor.getAllValues()).extracting(event -> event.payload().get("direction"))
                .containsExactly("ENTRY", "EXIT");

        verify(publisher).publish(entryMachine,
                "8ab2dd26-1cda-4f82-a989-ea09fa6d91b9", entryDecision);
        verify(publisher).publish(exitMachine,
                "a2d1bf25-e9a1-46e5-9044-f324caa2a4b4", exitDecision);
    }

    private TicketValidationDecision decision(String reference, String stationCode,
            int remainingTrips) {
        return new TicketValidationDecision(
                reference, TicketValidationStatus.ACCEPTED, "VALID", "RMM-TKT-001",
                stationCode, null, null, 0, remainingTrips, null, null,
                LocalDateTime.of(2026, 8, 11, 8, 0));
    }

    private AuthenticatedMqttMachine machine(long id, String code, DeviceType type,
            String stationCode) {
        return new AuthenticatedMqttMachine(
                id, code, type, stationCode, "validator-instance-" + id, code);
    }

    private AuthenticatedMqttMessage validationMessage(AuthenticatedMqttMachine machine,
            String messageId, String validationReference, String direction,
            String stationCode) {
        String payload = """
                {
                  "schemaVersion": 1,
                  "messageId": "%s",
                  "type": "ticket.validation-requested",
                  "deviceCode": "%s",
                  "occurredAt": "2026-08-11T08:00:00.000Z",
                  "sentAt": "2026-08-11T08:00:00.000Z",
                  "payload": {
                    "validationReference": "%s",
                    "direction": "%s",
                    "stationCode": "%s",
                    "qrValue": "RMM:TICKET:1:signed"
                  }
                }
                """.formatted(messageId, machine.deviceCode(), validationReference,
                direction, stationCode);
        return new AuthenticatedMqttMessage(
                machine,
                "rmm/v1/devices/" + machine.deviceCode() + "/requests/validations",
                payload.getBytes(StandardCharsets.UTF_8));
    }
}
