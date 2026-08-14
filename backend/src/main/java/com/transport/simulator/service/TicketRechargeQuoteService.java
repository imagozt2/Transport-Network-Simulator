package com.transport.simulator.service;

import com.transport.simulator.dto.request.ticketrecharge.TicketRechargeQuoteRequest;
import com.transport.simulator.dto.response.ticketrecharge.TicketRechargeQuoteResponse;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.service.model.TicketRechargeParameters;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketRechargeQuoteService {

    private final TicketRechargeLookupService lookupService;
    private final TicketRechargePricingService pricingService;

    public TicketRechargeQuoteService(
            TicketRechargeLookupService lookupService,
            TicketRechargePricingService pricingService
    ) {
        this.lookupService = lookupService;
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public TicketRechargeQuoteResponse quote(TicketRechargeQuoteRequest request) {
        Ticket ticket = lookupService.requireRechargeableTicket(request.qrValue())
                .credential().getTicket();
        TicketRechargeParameters parameters = new TicketRechargeParameters(
                request.originStationCode(), request.destinationStationCode(), request.trips(),
                request.days(), request.balanceAmount()
        );
        try {
            return TicketRechargeQuoteResponse.from(pricingService.quote(ticket, parameters));
        } catch (IllegalArgumentException | ServiceConfigurationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "INVALID_RECHARGE_CONFIGURATION",
                    exception
            );
        }
    }
}
