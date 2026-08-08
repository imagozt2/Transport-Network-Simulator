package com.transport.simulator.repository;

import com.transport.simulator.entity.TicketSupport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketSupportRepository extends JpaRepository<TicketSupport, Long> {

    boolean existsBySerialNumber(String serialNumber);

    Optional<TicketSupport> findByCode(String code);
}
