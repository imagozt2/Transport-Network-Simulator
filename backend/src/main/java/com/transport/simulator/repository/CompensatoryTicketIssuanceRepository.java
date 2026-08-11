package com.transport.simulator.repository;

import com.transport.simulator.entity.CompensatoryTicketIssuance;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompensatoryTicketIssuanceRepository
        extends JpaRepository<CompensatoryTicketIssuance, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select issuance from CompensatoryTicketIssuance issuance where issuance.code = :code")
    Optional<CompensatoryTicketIssuance> findByCodeForUpdate(@Param("code") String code);
}
