package com.transport.simulator.repository;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.enums.TicketJourneyStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketJourneyRepository extends JpaRepository<TicketJourney, Long> {

    Optional<TicketJourney> findFirstByTicketAndStatusOrderByOpenedAtDesc(
            Ticket ticket,
            TicketJourneyStatus status
    );

    List<TicketJourney> findAllByTicketIdInAndStatus(
            Collection<Long> ticketIds,
            TicketJourneyStatus status
    );

    List<TicketJourney> findAllByTicketAndPassengerAccountIsNull(Ticket ticket);
}
