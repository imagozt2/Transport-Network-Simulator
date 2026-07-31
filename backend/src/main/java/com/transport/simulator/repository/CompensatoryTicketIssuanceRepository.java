package com.transport.simulator.repository;

import com.transport.simulator.entity.CompensatoryTicketIssuance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompensatoryTicketIssuanceRepository
        extends JpaRepository<CompensatoryTicketIssuance, Long> {
}
