package com.transport.simulator.service;

import com.transport.simulator.dto.response.ticketrecharge.TicketRechargeLookupResponse;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.enums.TicketStatus;
import com.transport.simulator.enums.TicketSupportStatus;
import com.transport.simulator.ticketing.qr.TicketQrVerificationException;
import com.transport.simulator.ticketing.qr.TicketQrVerifier;
import com.transport.simulator.ticketing.qr.VerifiedTicketQr;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketRechargeLookupService {

    private final TicketQrVerifier qrVerifier;

    public TicketRechargeLookupService(TicketQrVerifier qrVerifier) {
        this.qrVerifier = qrVerifier;
    }

    @Transactional(readOnly = true)
    public TicketRechargeLookupResponse findRechargeableTicket(String qrValue) {
        VerifiedTicketQr verified = requireRechargeableTicket(qrValue);
        Ticket ticket = verified.credential().getTicket();

        return TicketRechargeLookupResponse.from(
                ticket,
                verified.credential().getSupport().getType()
        );
    }

    VerifiedTicketQr requireRechargeableTicket(String qrValue) {
        VerifiedTicketQr verified = verify(qrValue);
        Ticket ticket = verified.credential().getTicket();

        if (verified.credential().getSupport().getStatus() != TicketSupportStatus.ACTIVE
                || !ticket.isActive()
                || !ticket.getProduct().isActive()
                || !ticket.getProduct().isRechargeable()
                || !canRecharge(ticket)) {
            throw unprocessable("TICKET_NOT_RECHARGEABLE");
        }
        return verified;
    }

    private VerifiedTicketQr verify(String qrValue) {
        try {
            return qrVerifier.verify(qrValue);
        } catch (TicketQrVerificationException exception) {
            throw unprocessable("INVALID_TICKET_QR");
        }
    }

    private boolean canRecharge(Ticket ticket) {
        TicketStatus status = ticket.getStatus();
        return switch (ticket.getProductType()) {
            case SINGLE_TRIP -> status == TicketStatus.EXHAUSTED;
            case MULTI_TRIP -> (status == TicketStatus.ACTIVE || status == TicketStatus.EXHAUSTED)
                    && hasAvailableTripOption(ticket);
            case SMART_BALANCE -> status == TicketStatus.ACTIVE
                    || status == TicketStatus.EXHAUSTED;
            case TIME_PASS -> status == TicketStatus.ACTIVE || status == TicketStatus.EXPIRED;
        };
    }

    private boolean hasAvailableTripOption(Ticket ticket) {
        Integer minimum = ticket.getProduct().getMinTrips();
        Integer maximum = ticket.getProduct().getMaxTrips();
        int current = ticket.getRemainingTrips() == null ? 0 : ticket.getRemainingTrips();
        return minimum != null && maximum != null && current + minimum <= maximum;
    }

    private ResponseStatusException unprocessable(String reason) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, reason);
    }
}
