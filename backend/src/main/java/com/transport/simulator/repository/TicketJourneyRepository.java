package com.transport.simulator.repository;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.enums.TicketJourneyStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    List<TicketJourney> findAllByStatusAndOpenedAtBefore(
            TicketJourneyStatus status,
            LocalDateTime openedAt
    );

    Optional<TicketJourney> findByCodeAndPassengerAccountId(String code, Long passengerAccountId);

    @Query("""
            SELECT journey FROM TicketJourney journey
            WHERE journey.passengerAccount.id = :passengerId
              AND (:cursorOpenedAt IS NULL
                OR journey.openedAt < :cursorOpenedAt
                OR (journey.openedAt = :cursorOpenedAt AND journey.id < :cursorId))
            ORDER BY journey.openedAt DESC, journey.id DESC
            """)
    List<TicketJourney> findPassengerJourneyHistory(
            @Param("passengerId") Long passengerId,
            @Param("cursorOpenedAt") LocalDateTime cursorOpenedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
