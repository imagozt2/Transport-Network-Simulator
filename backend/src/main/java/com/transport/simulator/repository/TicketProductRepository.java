package com.transport.simulator.repository;

import com.transport.simulator.entity.TicketProduct;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketProductRepository extends JpaRepository<TicketProduct, Long> {

    List<TicketProduct> findAllByOrderByCodeAsc();

    Optional<TicketProduct> findByCodeIgnoreCase(String code);
}
