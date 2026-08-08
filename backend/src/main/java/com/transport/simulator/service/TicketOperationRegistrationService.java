package com.transport.simulator.service;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.Purchase;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.entity.TicketOperation;
import com.transport.simulator.entity.TicketSupport;
import com.transport.simulator.enums.PurchaseOrigin;
import com.transport.simulator.enums.TicketOperationSource;
import com.transport.simulator.enums.TicketOperationType;
import com.transport.simulator.repository.TicketOperationRepository;
import com.transport.simulator.service.model.TicketSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TicketOperationRegistrationService {

    private final TicketOperationRepository repository;
    private final Clock clock;

    public TicketOperationRegistrationService(TicketOperationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public TicketOperation recordIssuance(Ticket ticket, TicketSupport support) {
        TicketOperation operation = create(
                ticket, TicketOperationType.ISSUED, TicketOperationSource.SYSTEM,
                null, TicketSnapshot.from(ticket), null
        );
        operation.relateToSupport(support);
        operation.recordContext(support.getIssuedByDevice(), ticket.getPassengerAccount(), null);
        return repository.save(operation);
    }

    public TicketOperation recordJourney(
            TicketOperationType type,
            Ticket ticket,
            TicketJourney journey,
            Station station,
            TicketSnapshot before,
            BigDecimal amount
    ) {
        TicketOperation operation = create(
                ticket, type, TicketOperationSource.SYSTEM, before, TicketSnapshot.from(ticket), amount
        );
        operation.relateToJourney(journey, station);
        operation.recordContext(null, ticket.getPassengerAccount(), null);
        return repository.save(operation);
    }

    public TicketOperation recordRecharge(
            Purchase purchase,
            TicketSnapshot before,
            Device device,
            PassengerAccount passenger
    ) {
        TicketOperation operation = create(
                purchase.getTicket(), TicketOperationType.RECHARGED, source(purchase.getOrigin()),
                before, TicketSnapshot.from(purchase.getTicket()), purchase.getTotalAmount()
        );
        operation.relateToPurchase(purchase);
        operation.recordContext(device, passenger, purchase.getExternalReference());
        return repository.save(operation);
    }

    private TicketOperation create(
            Ticket ticket,
            TicketOperationType type,
            TicketOperationSource source,
            TicketSnapshot before,
            TicketSnapshot after,
            BigDecimal amount
    ) {
        return new TicketOperation(
                uniqueCode(), ticket, type, source, before, after, amount, LocalDateTime.now(clock)
        );
    }

    private TicketOperationSource source(PurchaseOrigin origin) {
        return switch (origin) {
            case RMM_APP -> TicketOperationSource.RMM_APP;
            case TICKET_MACHINE -> TicketOperationSource.TICKET_MACHINE;
            case CONTROL_CENTER -> TicketOperationSource.CONTROL_CENTER;
        };
    }

    private String uniqueCode() {
        return "RMM-OP-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }
}
