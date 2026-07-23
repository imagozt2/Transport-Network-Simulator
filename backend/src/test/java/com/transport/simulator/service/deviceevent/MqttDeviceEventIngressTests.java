package com.transport.simulator.service.deviceevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.repository.DeviceEventLogRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
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
class MqttDeviceEventIngressTests {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Europe/Madrid");
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-23T08:30:00Z");

    @Mock
    private DeviceEventLogRepository eventLogRepository;
    @Mock
    private DeviceEventRegistrationService registrationService;

    private MqttDeviceEventIngress ingress;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ingress = new MqttDeviceEventIngress(
                eventLogRepository,
                registrationService,
                new ObjectMapper(),
                validator,
                Clock.fixed(OCCURRED_AT, SERVICE_ZONE)
        );
    }

    @Test
    void shouldConvertAndRegisterAValidMqttMessage() {
        DeviceEventMessage message = validMessage();
        DeviceEventLog savedLog = org.mockito.Mockito.mock(DeviceEventLog.class);
        when(savedLog.getId()).thenReturn(42L);
        when(eventLogRepository.findByOriginAndExternalReference(
                LogOrigin.MQTT,
                message.eventId()
        )).thenReturn(Optional.empty());
        when(registrationService.register(any(DeviceEvent.class))).thenReturn(savedLog);
        ArgumentCaptor<DeviceEvent> captor = ArgumentCaptor.forClass(DeviceEvent.class);

        DeviceEventReceipt receipt = ingress.receive(message);

        verify(registrationService).register(captor.capture());
        DeviceEvent event = captor.getValue();
        assertThat(event.origin()).isEqualTo(LogOrigin.MQTT);
        assertThat(event.deviceCode()).isEqualTo(message.deviceCode());
        assertThat(event.externalReference()).isEqualTo(message.eventId());
        assertThat(event.occurredAt())
                .isEqualTo(LocalDateTime.ofInstant(OCCURRED_AT, SERVICE_ZONE));
        assertThat(event.payloadJson()).contains("\"temperature\":37");
        assertThat(receipt.status()).isEqualTo(DeviceEventReceipt.Status.ACCEPTED);
        assertThat(receipt.logId()).isEqualTo(42L);
    }

    @Test
    void shouldTreatARetriedEventAsDuplicateWithoutRegisteringItAgain() {
        DeviceEventMessage message = validMessage();
        DeviceEventLog existingLog = org.mockito.Mockito.mock(DeviceEventLog.class);
        when(existingLog.getId()).thenReturn(12L);
        when(eventLogRepository.findByOriginAndExternalReference(
                LogOrigin.MQTT,
                message.eventId()
        )).thenReturn(Optional.of(existingLog));

        DeviceEventReceipt receipt = ingress.receive(message);

        assertThat(receipt.status()).isEqualTo(DeviceEventReceipt.Status.DUPLICATE);
        assertThat(receipt.logId()).isEqualTo(12L);
        verify(registrationService, never()).register(any());
    }

    @Test
    void shouldRejectInvalidAndUnsupportedMessages() {
        DeviceEventMessage invalid = new DeviceEventMessage(
                DeviceEventMessage.CURRENT_SCHEMA_VERSION,
                "",
                "",
                null,
                null,
                "",
                null,
                null
        );
        DeviceEventMessage unsupported = new DeviceEventMessage(
                "2.0",
                "evt-002",
                "TVM-ST001-01",
                DeviceEventType.DEVICE_ONLINE,
                LogSeverity.INFO,
                "Conexión",
                OCCURRED_AT,
                Map.of()
        );

        assertThatThrownBy(() -> ingress.receive(invalid))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        assertThatThrownBy(() -> ingress.receive(unsupported))
                .isInstanceOf(UnsupportedDeviceEventSchemaException.class);
        verify(registrationService, never()).register(any());
    }

    private DeviceEventMessage validMessage() {
        return new DeviceEventMessage(
                DeviceEventMessage.CURRENT_SCHEMA_VERSION,
                "evt-001",
                "TVM-ST001-01",
                DeviceEventType.DEVICE_ONLINE,
                LogSeverity.INFO,
                "Máquina conectada",
                OCCURRED_AT,
                Map.of("temperature", 37)
        );
    }
}
