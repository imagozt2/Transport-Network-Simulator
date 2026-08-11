package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.TicketJourneyStatus;
import com.transport.simulator.enums.TicketOperationType;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketJourneyRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.service.model.NetworkJourney;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PassengerEntryJourneyExitIntegrationTests {

    @Test
    void shouldOpenAJourneyOnEntryAndCloseTheSameJourneyOnExit() {
        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketJourneyRepository journeyRepository = mock(TicketJourneyRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);
        NetworkJourneyPlanningService journeyPlanningService = mock(NetworkJourneyPlanningService.class);
        TicketOperationRegistrationService operationRegistrationService =
                mock(TicketOperationRegistrationService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-11T08:00:00Z"));

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

        ArgumentCaptor<TicketOperationType> operationType =
                ArgumentCaptor.forClass(TicketOperationType.class);
        verify(operationRegistrationService, org.mockito.Mockito.times(2)).recordJourney(
                operationType.capture(), any(), any(), any(), any(), any()
        );
        assertThat(operationType.getAllValues())
                .containsExactly(TicketOperationType.ENTRY_ACCEPTED, TicketOperationType.EXIT_ACCEPTED);
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
