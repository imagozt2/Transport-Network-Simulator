package com.transport.simulator.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.enums.TicketJourneyStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TicketJourneyTests {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 8, 11, 10, 0);

    @Test
    void buildsAnOpenJourneyWithItsPassengerSnapshot() {
        PassengerAccount passenger = mock(PassengerAccount.class);
        Ticket ticket = ticket(passenger);
        Station entry = station("ST001");

        TicketJourney journey = new TicketJourney("RMM-JRN-001", ticket, entry, OPENED_AT);

        assertThat(journey.getStatus()).isEqualTo(TicketJourneyStatus.OPEN);
        assertThat(journey.getPassengerAccount()).isSameAs(passenger);
        assertThat(journey.getEntryStation()).isSameAs(entry);
        assertThat(journey.getExitStation()).isNull();
        assertThat(journey.getCurrency()).isEqualTo("EUR");
        assertThat(journey.isAnomalous()).isFalse();
    }

    @Test
    void closesAJourneyWithItsRouteAndFare() {
        TicketJourney journey = new TicketJourney(
                "RMM-JRN-001", ticket(null), station("ST001"), OPENED_AT
        );
        Station exit = station("ST005");

        journey.close(exit, 5, new BigDecimal("0.75"), OPENED_AT.plusMinutes(12));

        assertThat(journey.getStatus()).isEqualTo(TicketJourneyStatus.CLOSED);
        assertThat(journey.getExitStation()).isSameAs(exit);
        assertThat(journey.getStationCount()).isEqualTo(5);
        assertThat(journey.getFareAmount()).isEqualByComparingTo("0.75");
        assertThat(journey.getClosedAt()).isEqualTo(OPENED_AT.plusMinutes(12));
    }

    @Test
    void marksAnEntryWithoutExitAsAnAnomaly() {
        TicketJourney journey = new TicketJourney(
                "RMM-JRN-001", ticket(null), station("ST001"), OPENED_AT
        );

        journey.forceClose(OPENED_AT.plusHours(6));

        assertThat(journey.getStatus()).isEqualTo(TicketJourneyStatus.FORCED_CLOSED);
        assertThat(journey.getForcedClosedAt()).isEqualTo(OPENED_AT.plusHours(6));
        assertThat(journey.getExitStation()).isNull();
        assertThat(journey.getStationCount()).isNull();
        assertThat(journey.getFareAmount()).isNull();
        assertThat(journey.isAnomalous()).isTrue();
    }

    @Test
    void rejectsInvalidOrRepeatedClosures() {
        TicketJourney journey = new TicketJourney(
                "RMM-JRN-001", ticket(null), station("ST001"), OPENED_AT
        );

        assertThatThrownBy(() -> journey.forceClose(OPENED_AT.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        journey.forceClose(OPENED_AT.plusMinutes(1));
        assertThatThrownBy(() -> journey.close(
                station("ST002"), 2, BigDecimal.ONE, OPENED_AT.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    private Ticket ticket(PassengerAccount passenger) {
        Ticket ticket = mock(Ticket.class);
        when(ticket.getPassengerAccount()).thenReturn(passenger);
        when(ticket.getCurrency()).thenReturn("EUR");
        return ticket;
    }

    private Station station(String code) {
        Station station = mock(Station.class);
        when(station.getCode()).thenReturn(code);
        return station;
    }
}
