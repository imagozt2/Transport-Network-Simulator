package com.transport.simulator.repository;

import com.transport.simulator.entity.Ticket;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByCode(String code);

    Optional<Ticket> findByCodeAndPassengerAccountId(String code, Long passengerAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from Ticket ticket where ticket.code = :code")
    Optional<Ticket> findByCodeForUpdate(@Param("code") String code);
}
