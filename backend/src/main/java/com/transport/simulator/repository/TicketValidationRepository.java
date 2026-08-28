package com.transport.simulator.repository;

import com.transport.simulator.entity.TicketValidation;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketValidationRepository extends JpaRepository<TicketValidation, Long> {
    @EntityGraph(attributePaths = {"ticket", "station"})
    Optional<TicketValidation> findByExternalReference(String externalReference);
}
