package com.transport.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuthenticatedMqttMessageRouterTests {
    @Mock private ControlCenterMqttClient mqttClient;
    @Mock private MqttMachineAuthenticationService authenticationService;
    @Mock private MqttInboundIdempotencyService idempotencyService;

    @Test
    void shouldReceiveAnInboundMessageOnlyOnceWhenTheBrokerRedeliversIt() {
        Map<String, BiConsumer<String, byte[]>> subscriptions = new HashMap<>();
        doAnswer(invocation -> {
            subscriptions.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(mqttClient).subscribe(anyString(), anyInt(), any());
        AuthenticatedMqttMachine machine = new AuthenticatedMqttMachine(
                21L, "TVM-ST001-01",
                com.transport.simulator.enums.DeviceType.TICKET_MACHINE,
                "ST001", "instance-001", "TVM-ST001-01");
        String topic = "rmm/v1/devices/TVM-ST001-01/events/sales";
        String messageId = "8ef4e572-1a2c-4aa9-957a-8bf683272f64";
        byte[] payload = ("{\"schemaVersion\":1,\"messageId\":\"" + messageId
                + "\",\"deviceCode\":\"TVM-ST001-01\"}")
                .getBytes(StandardCharsets.UTF_8);
        when(authenticationService.authenticate(topic, "TVM-ST001-01")).thenReturn(machine);
        when(idempotencyService.claim(messageId, 21L, topic, payload))
                .thenReturn(MqttIdempotencyClaim.PROCESS, MqttIdempotencyClaim.DUPLICATE);
        AuthenticatedMqttMessageRouter router = new AuthenticatedMqttMessageRouter(
                mqttClient, authenticationService, new ObjectMapper(), idempotencyService);
        AtomicInteger received = new AtomicInteger();
        router.register(ignored -> received.incrementAndGet());

        BiConsumer<String, byte[]> receiver = subscriptions.get("rmm/v1/devices/+/events/+");
        receiver.accept(topic, payload);
        receiver.accept(topic, payload);

        assertThat(received).hasValue(1);
        verify(idempotencyService).complete(messageId);
    }
}
