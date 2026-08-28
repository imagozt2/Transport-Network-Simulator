package com.transport.simulator.repository;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByCode(String code);

    Optional<Ticket> findByCodeAndPassengerAccountId(String code, Long passengerAccountId);

    @Query("""
            select ticket from Ticket ticket
            join fetch ticket.product product
            where ticket.passengerAccount.id = :passengerAccountId
              and (:status is null or ticket.status = :status)
              and (:productType is null or ticket.productType = :productType)
              and (:cursorIssuedAt is null
                   or ticket.issuedAt < :cursorIssuedAt
                   or (ticket.issuedAt = :cursorIssuedAt and ticket.id < :cursorId))
            order by ticket.issuedAt desc, ticket.id desc
            """)
    List<Ticket> findPassengerWallet(
            @Param("passengerAccountId") Long passengerAccountId,
            @Param("status") TicketStatus status,
            @Param("productType") TicketProductType productType,
            @Param("cursorIssuedAt") LocalDateTime cursorIssuedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from Ticket ticket where ticket.code = :code")
    Optional<Ticket> findByCodeForUpdate(@Param("code") String code);
}
