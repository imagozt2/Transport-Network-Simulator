package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.enums.TicketJourneyStatus;
import com.transport.simulator.repository.TicketJourneyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TicketJourneyAnomalyServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 11, 12, 0);

    private final TicketJourneyRepository repository = mock(TicketJourneyRepository.class);
    private final TicketJourneyAnomalyService service = new TicketJourneyAnomalyService(
            repository, CLOCK, Duration.ofHours(6)
    );

    @Test
    void forceClosesThePreviousJourneyWhenANewEntryRevealsAMissingExit() {
        Ticket ticket = ticket();
        TicketJourney openJourney = journey(ticket, NOW.minusMinutes(30));
        when(repository.findFirstByTicketAndStatusOrderByOpenedAtDesc(
                ticket, TicketJourneyStatus.OPEN)).thenReturn(Optional.of(openJourney));
        when(repository.save(openJourney)).thenReturn(openJourney);

        var result = service.forceCloseJourneyWithoutExit(ticket);

        assertThat(result).contains(openJourney);
        assertThat(openJourney.getStatus()).isEqualTo(TicketJourneyStatus.FORCED_CLOSED);
        assertThat(openJourney.getForcedClosedAt()).isEqualTo(NOW);
        verify(repository).save(openJourney);
    }

    @Test
    void periodicallyClosesOnlyJourneysOlderThanTheConfiguredLimit() {
        TicketJourney first = journey(ticket(), NOW.minusHours(7));
        TicketJourney second = journey(ticket(), NOW.minusHours(8));
        LocalDateTime threshold = NOW.minusHours(6);
        when(repository.findAllByStatusAndOpenedAtBefore(
                TicketJourneyStatus.OPEN, threshold)).thenReturn(List.of(first, second));

        int count = service.forceCloseExpiredOpenJourneys();

        assertThat(count).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(TicketJourneyStatus.FORCED_CLOSED);
        assertThat(second.getStatus()).isEqualTo(TicketJourneyStatus.FORCED_CLOSED);
        verify(repository).saveAll(List.of(first, second));
    }

    @Test
    void leavesTheSystemUntouchedWhenThereAreNoOpenJourneys() {
        Ticket ticket = ticket();
        when(repository.findFirstByTicketAndStatusOrderByOpenedAtDesc(
                ticket, TicketJourneyStatus.OPEN)).thenReturn(Optional.empty());

        assertThat(service.forceCloseJourneyWithoutExit(ticket)).isEmpty();
    }

    private TicketJourney journey(Ticket ticket, LocalDateTime openedAt) {
        return new TicketJourney("RMM-JRN-" + openedAt, ticket, mock(Station.class), openedAt);
    }

    private Ticket ticket() {
        Ticket ticket = mock(Ticket.class);
        when(ticket.getCurrency()).thenReturn("EUR");
        return ticket;
    }
}
