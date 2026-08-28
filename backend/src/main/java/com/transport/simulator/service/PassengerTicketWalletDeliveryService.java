package com.transport.simulator.service;

import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.enums.TicketSupportStatus;
import com.transport.simulator.enums.TicketSupportType;
import com.transport.simulator.service.model.IssuedTicket;
import com.transport.simulator.service.model.TicketIssuanceParameters;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers an administratively issued digital ticket to a passenger wallet.
 *
 * <p>The wallet does not need a separate copy of the ticket: ownership on the
 * ticket and its active digital support make it immediately available through
 * the passenger ticket queries.</p>
 */
@Service
public class PassengerTicketWalletDeliveryService {

    private final TicketIssuanceService ticketIssuanceService;

    public PassengerTicketWalletDeliveryService(TicketIssuanceService ticketIssuanceService) {
        this.ticketIssuanceService = ticketIssuanceService;
    }

    @Transactional
    public IssuedTicket deliver(
            TicketProduct product,
            TicketIssuanceParameters parameters,
            PassengerAccount passenger
    ) {
        Objects.requireNonNull(passenger, "passenger is required");
        if (passenger.getStatus() != PassengerAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("A digital ticket requires an active passenger account");
        }

        IssuedTicket issued = ticketIssuanceService.issueDigital(product, parameters, passenger);
        verifyWalletOwnership(issued, passenger);
        return issued;
    }

    private void verifyWalletOwnership(IssuedTicket issued, PassengerAccount passenger) {
        if (issued == null || issued.ticket() == null || issued.support() == null) {
            throw new IllegalStateException("Digital ticket delivery did not create a ticket and support");
        }
        if (issued.ticket().getPassengerAccount() != passenger
                || issued.support().getPassengerAccount() != passenger) {
            throw new IllegalStateException("Digital ticket delivery targeted a different passenger wallet");
        }
        if (issued.support().getTicket() != issued.ticket()
                || issued.support().getType() != TicketSupportType.DIGITAL
                || issued.support().getStatus() != TicketSupportStatus.ACTIVE) {
            throw new IllegalStateException("Digital ticket delivery did not create an active wallet support");
        }
    }
}
