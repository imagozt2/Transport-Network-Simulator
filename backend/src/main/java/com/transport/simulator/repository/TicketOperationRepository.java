package com.transport.simulator.repository;

import com.transport.simulator.entity.TicketOperation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketOperationRepository extends JpaRepository<TicketOperation, Long> {
    List<TicketOperation> findAllByTicketCodeOrderByOccurredAtDescIdDesc(String ticketCode);
}
