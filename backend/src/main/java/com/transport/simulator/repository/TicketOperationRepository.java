package com.transport.simulator.repository;

import com.transport.simulator.entity.TicketOperation;
import com.transport.simulator.enums.TicketOperationSource;
import com.transport.simulator.enums.TicketOperationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketOperationRepository extends JpaRepository<TicketOperation, Long> {
    List<TicketOperation> findAllByTicketCodeOrderByOccurredAtDescIdDesc(String ticketCode);

    Optional<TicketOperation> findFirstByTypeAndSourceAndExternalReferenceStartingWith(
            TicketOperationType type,
            TicketOperationSource source,
            String externalReferencePrefix
    );
}
