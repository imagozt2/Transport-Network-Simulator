package com.transport.simulator.repository;

import com.transport.simulator.entity.TicketQrUseClaim;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketQrUseClaimRepository extends JpaRepository<TicketQrUseClaim, Long> {

    Optional<TicketQrUseClaim> findByValidationReference(String validationReference);
}
