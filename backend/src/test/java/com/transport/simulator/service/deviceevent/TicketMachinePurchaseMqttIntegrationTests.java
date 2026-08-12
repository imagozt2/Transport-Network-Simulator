package com.transport.simulator.service.deviceevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.mqtt.AuthenticatedMqttMachine;
import com.transport.simulator.mqtt.AuthenticatedMqttMessage;
import com.transport.simulator.mqtt.AuthenticatedMqttMessageRouter;
import com.transport.simulator.mqtt.MqttTicketValidationResponsePublisher;
import com.transport.simulator.service.TicketMachinePurchaseService;
import com.transport.simulator.service.TicketValidationService;
import com.transport.simulator.service.model.TicketMachinePurchaseRequest;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class TicketMachinePurchaseMqttIntegrationTests {

    @Test
    void shouldReceiveAQtTicketMachinePurchaseAndPreserveItsCorrelationData() {
        AuthenticatedMqttMessageRouter router = mock(AuthenticatedMqttMessageRouter.class);
        DeviceEventIngress ingress = mock(DeviceEventIngress.class);
        TicketMachinePurchaseService purchaseService = mock(TicketMachinePurchaseService.class);
        when(ingress.receive(any())).thenReturn(new DeviceEventReceipt(
                "73bb91e8-b263-41e8-aa8f-b791480110b3", 91L,
                DeviceEventReceipt.Status.ACCEPTED
        ));

        new MqttTicketOperationEventReceiver(
                router,
                ingress,
                new ObjectMapper(),
                purchaseService,
                mock(TicketValidationService.class),
                mock(MqttTicketValidationResponsePublisher.class)
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<AuthenticatedMqttMessage>> consumerCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(router).register(consumerCaptor.capture());

        String payload = """
                {
                  "schemaVersion": 1,
                  "messageId": "73bb91e8-b263-41e8-aa8f-b791480110b3",
                  "correlationId": null,
                  "type": "ticket.purchase-requested",
                  "deviceCode": "RMM-TM-ST001-01",
                  "occurredAt": "2026-08-11T12:00:00.000Z",
                  "sentAt": "2026-08-11T12:00:00.000Z",
                  "payload": {
                    "purchaseReference": "9561ad31-6273-42d9-b76f-2dabb0b60955",
                    "productCode": "SINGLE_TRIP",
                    "paymentMethod": "SIMULATED",
                    "paidAmount": 0.85,
                    "currency": "EUR",
                    "configuration": {
                      "originStationCode": "ST001",
                      "destinationStationCode": "ST007"
                    }
                  }
                }
                """;
        AuthenticatedMqttMachine machine = new AuthenticatedMqttMachine(
                41L, "RMM-TM-ST001-01", DeviceType.TICKET_MACHINE,
                "ST001", "ticket-machine-01", "RMM-TM-ST001-01"
        );

        consumerCaptor.getValue().accept(new AuthenticatedMqttMessage(
                machine,
                "rmm/v1/devices/RMM-TM-ST001-01/requests/purchases",
                payload.getBytes(StandardCharsets.UTF_8)
        ));

        ArgumentCaptor<DeviceEventMessage> eventCaptor =
                ArgumentCaptor.forClass(DeviceEventMessage.class);
        verify(ingress).receive(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(DeviceEventType.TICKET_PURCHASE_REQUESTED);
        assertThat(eventCaptor.getValue().deviceCode()).isEqualTo("RMM-TM-ST001-01");
        assertThat(eventCaptor.getValue().payload())
                .containsEntry("purchaseReference", "9561ad31-6273-42d9-b76f-2dabb0b60955")
                .containsEntry("productCode", "SINGLE_TRIP");

        ArgumentCaptor<TicketMachinePurchaseRequest> purchaseCaptor =
                ArgumentCaptor.forClass(TicketMachinePurchaseRequest.class);
        verify(purchaseService).purchase(org.mockito.ArgumentMatchers.eq(41L), purchaseCaptor.capture());
        TicketMachinePurchaseRequest request = purchaseCaptor.getValue();
        assertThat(request.purchaseReference()).isEqualTo("9561ad31-6273-42d9-b76f-2dabb0b60955");
        assertThat(request.productCode()).isEqualTo("SINGLE_TRIP");
        assertThat(request.originStationCode()).isEqualTo("ST001");
        assertThat(request.destinationStationCode()).isEqualTo("ST007");
        assertThat(request.paidAmount()).isEqualByComparingTo("0.85");
    }
}
