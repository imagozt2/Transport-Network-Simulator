package com.transport.simulator.repository;

import com.transport.simulator.entity.TicketSupport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketSupportRepository extends JpaRepository<TicketSupport, Long> {

    boolean existsBySerialNumber(String serialNumber);

    Optional<TicketSupport> findByCode(String code);

    @org.springframework.data.jpa.repository.Query("""
            select support from TicketSupport support
            where support.code = :code
              and support.ticket.passengerAccount.id = :passengerAccountId
            """)
    Optional<TicketSupport> findOwnedByCode(
            @org.springframework.data.repository.query.Param("code") String code,
            @org.springframework.data.repository.query.Param("passengerAccountId") Long passengerAccountId
    );
}
