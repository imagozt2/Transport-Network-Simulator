package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.entity.TicketOperation;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.TicketJourneyStatus;
import com.transport.simulator.enums.TicketOperationType;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.mqtt.AuthenticatedMqttMachine;
import com.transport.simulator.mqtt.AuthenticatedMqttMessage;
import com.transport.simulator.mqtt.AuthenticatedMqttMessageRouter;
import com.transport.simulator.mqtt.MqttTicketValidationResponsePublisher;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketJourneyRepository;
import com.transport.simulator.repository.TicketOperationRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.service.deviceevent.DeviceEventIngress;
import com.transport.simulator.service.deviceevent.DeviceEventMessage;
import com.transport.simulator.service.deviceevent.MqttTicketOperationEventReceiver;
import com.transport.simulator.service.model.NetworkJourney;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class PassengerEntryJourneyExitIntegrationTests {

    @Test
    void shouldOpenAJourneyOnEntryAndCloseTheSameJourneyOnExit() {
        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketJourneyRepository journeyRepository = mock(TicketJourneyRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);
        NetworkJourneyPlanningService journeyPlanningService = mock(NetworkJourneyPlanningService.class);
        TicketOperationRepository operationRepository = mock(TicketOperationRepository.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-11T08:00:00Z"));
        TicketOperationRegistrationService operationRegistrationService =
                new TicketOperationRegistrationService(operationRepository, clock);
        List<TicketOperation> storedOperations = new ArrayList<>();
        when(operationRepository.save(any(TicketOperation.class))).thenAnswer(invocation -> {
            TicketOperation operation = invocation.getArgument(0);
            storedOperations.add(operation);
            return operation;
        });

        TicketProduct product = mock(TicketProduct.class);
        when(product.getProductType()).thenReturn(TicketProductType.MULTI_TRIP);
        when(product.getMinTrips()).thenReturn(2);
        when(product.getMaxTrips()).thenReturn(30);
        when(product.getPricePerTrip()).thenReturn(new BigDecimal("1.00"));
        Ticket ticket = new Ticket(
                "RMM-TKT-JOURNEY-001", "qr-token-journey-001", product,
                java.time.LocalDateTime.ofInstant(clock.instant(), clock.getZone())
        );
        ticket.configureTripBalance(10);
        Station entryStation = new Station("ST001", "Aeropuerto");
        Station exitStation = new Station("ST010", "Gueto Norte");

        when(ticketRepository.findByCodeForUpdate("RMM-TKT-JOURNEY-001"))
                .thenReturn(Optional.of(ticket));
        when(stationRepository.findByCodeAndActiveTrue("ST001"))
                .thenReturn(Optional.of(entryStation));
        when(stationRepository.findByCodeAndActiveTrue("ST010"))
                .thenReturn(Optional.of(exitStation));
        when(journeyPlanningService.calculate("ST001", "ST010"))
                .thenReturn(new NetworkJourney(
                        null, null, 7, 0, 720, List.of(), List.of()
                ));

        AtomicReference<TicketJourney> storedJourney = new AtomicReference<>();
        when(journeyRepository.findFirstByTicketAndStatusOrderByOpenedAtDesc(
                ticket, TicketJourneyStatus.OPEN
        )).thenAnswer(invocation -> Optional.ofNullable(storedJourney.get())
                .filter(journey -> journey.getStatus() == TicketJourneyStatus.OPEN));
        when(journeyRepository.save(any(TicketJourney.class))).thenAnswer(invocation -> {
            TicketJourney journey = invocation.getArgument(0);
            storedJourney.set(journey);
            return journey;
        });

        MultiTripTicketService service = new MultiTripTicketService(
                ticketRepository,
                journeyRepository,
                stationRepository,
                new TicketJourneySettlementService(journeyPlanningService),
                operationRegistrationService,
                clock
        );

        TicketJourney opened = service.enter("rmm-tkt-journey-001", "st001");

        assertThat(opened.getStatus()).isEqualTo(TicketJourneyStatus.OPEN);
        assertThat(opened.getEntryStation()).isSameAs(entryStation);
        assertThat(opened.getExitStation()).isNull();
        assertThat(ticket.getRemainingTrips()).isEqualTo(9);
        assertThat(opened.getOpenedAt()).isEqualTo("2026-08-11T08:00:00");

        clock.advanceSeconds(720);
        TicketJourney closed = service.exit("RMM-TKT-JOURNEY-001", "ST010");

        assertThat(closed).isSameAs(opened);
        assertThat(closed.getStatus()).isEqualTo(TicketJourneyStatus.CLOSED);
        assertThat(closed.getEntryStation()).isSameAs(entryStation);
        assertThat(closed.getExitStation()).isSameAs(exitStation);
        assertThat(closed.getStationCount()).isEqualTo(7);
        assertThat(closed.getFareAmount()).isEqualByComparingTo("1.00");
        assertThat(closed.getClosedAt()).isEqualTo("2026-08-11T08:12:00");
        assertThat(ticket.getRemainingTrips()).isEqualTo(9);

        assertThat(storedOperations).extracting(TicketOperation::getType)
                .containsExactly(TicketOperationType.ENTRY_ACCEPTED, TicketOperationType.EXIT_ACCEPTED);
        assertThat(storedOperations).allSatisfy(operation -> {
            assertThat(operation.getTicket()).isSameAs(ticket);
            assertThat(operation.getJourney()).isSameAs(closed);
        });
        assertThat(storedOperations).extracting(operation -> operation.getStation().getCode())
                .containsExactly("ST001", "ST010");

        List<DeviceEventMessage> logs = receiveValidatorLogs(ticket, closed);

        assertThat(logs).extracting(DeviceEventMessage::type)
                .containsExactly(DeviceEventType.VALIDATION_ACCEPTED,
                        DeviceEventType.VALIDATION_ACCEPTED);
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.payload()).containsEntry("ticketCode", ticket.getCode());
            assertThat(log.payload()).containsEntry("journeyCode", closed.getCode());
        });
        assertThat(logs).extracting(log -> log.payload().get("direction"))
                .containsExactly("ENTRY", "EXIT");
        assertThat(logs).extracting(log -> log.payload().get("stationCode"))
                .containsExactly("ST001", "ST010");
    }

    private List<DeviceEventMessage> receiveValidatorLogs(Ticket ticket, TicketJourney journey) {
        AuthenticatedMqttMessageRouter router = mock(AuthenticatedMqttMessageRouter.class);
        DeviceEventIngress ingress = mock(DeviceEventIngress.class);
        new MqttTicketOperationEventReceiver(
                router, ingress, new ObjectMapper(), mock(TicketMachinePurchaseService.class),
                mock(TicketValidationService.class),
                mock(MqttTicketValidationResponsePublisher.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<AuthenticatedMqttMessage>> consumerCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(router).register(consumerCaptor.capture());
        AuthenticatedMqttMachine entryMachine = new AuthenticatedMqttMachine(
                51L, "RMM-ENTRY-ST001-01", DeviceType.ENTRY_VALIDATOR,
                "ST001", "validator-instance-01", "RMM-ENTRY-ST001-01");
        AuthenticatedMqttMachine exitMachine = new AuthenticatedMqttMachine(
                52L, "RMM-EXIT-ST010-01", DeviceType.EXIT_VALIDATOR,
                "ST010", "validator-instance-02", "RMM-EXIT-ST010-01");

        consumerCaptor.getValue().accept(operationLog(entryMachine, ticket, journey,
                "a864635c-da08-42a5-a556-a3e76bae62e7", "ENTRY", "ST001",
                "2026-08-11T08:00:00Z"));
        consumerCaptor.getValue().accept(operationLog(exitMachine, ticket, journey,
                "0d6c36fb-ee4a-4c85-957e-009b02795d06", "EXIT", "ST010",
                "2026-08-11T08:12:00Z"));

        ArgumentCaptor<DeviceEventMessage> logCaptor =
                ArgumentCaptor.forClass(DeviceEventMessage.class);
        verify(ingress, org.mockito.Mockito.times(2)).receive(logCaptor.capture());
        return logCaptor.getAllValues();
    }

    private AuthenticatedMqttMessage operationLog(AuthenticatedMqttMachine machine,
            Ticket ticket, TicketJourney journey, String messageId, String direction,
            String stationCode, String occurredAt) {
        String payload = """
                {
                  "schemaVersion": 1,
                  "messageId": "%s",
                  "type": "device.operation-event",
                  "deviceCode": "%s",
                  "occurredAt": "%s",
                  "payload": {
                    "eventCode": "VALIDATION_ACCEPTED",
                    "severity": "INFO",
                    "details": {
                      "ticketCode": "%s",
                      "journeyCode": "%s",
                      "direction": "%s",
                      "stationCode": "%s"
                    }
                  }
                }
                """.formatted(messageId, machine.deviceCode(), occurredAt, ticket.getCode(),
                journey.getCode(), direction, stationCode);
        return new AuthenticatedMqttMessage(machine,
                "rmm/v1/devices/" + machine.deviceCode() + "/events/operation",
                payload.getBytes(StandardCharsets.UTF_8));
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
