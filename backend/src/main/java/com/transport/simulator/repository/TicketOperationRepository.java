package com.transport.simulator.repository;

import com.transport.simulator.entity.TicketOperation;
import com.transport.simulator.enums.TicketOperationSource;
import com.transport.simulator.enums.TicketOperationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface TicketOperationRepository extends JpaRepository<TicketOperation, Long> {
    List<TicketOperation> findAllByTicketCodeOrderByOccurredAtDescIdDesc(String ticketCode);

    Optional<TicketOperation> findByCodeAndTicketId(String code, Long ticketId);

    @Query("""
            select operation
            from TicketOperation operation
            left join fetch operation.station
            where operation.ticket.id = :ticketId
              and (:cursorTime is null
                or operation.occurredAt < :cursorTime
                or (operation.occurredAt = :cursorTime and operation.id < :cursorId))
            order by operation.occurredAt desc, operation.id desc
            """)
    List<TicketOperation> findPassengerHistory(
            @Param("ticketId") Long ticketId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    Optional<TicketOperation> findFirstByTypeAndSourceAndExternalReferenceStartingWith(
            TicketOperationType type,
            TicketOperationSource source,
            String externalReferencePrefix
    );
}
