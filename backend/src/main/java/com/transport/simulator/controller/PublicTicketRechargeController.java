package com.transport.simulator.controller;

import com.transport.simulator.dto.request.ticketrecharge.TicketRechargeLookupRequest;
import com.transport.simulator.dto.request.ticketrecharge.TicketRechargeQuoteRequest;
import com.transport.simulator.dto.response.ticketrecharge.TicketRechargeLookupResponse;
import com.transport.simulator.dto.response.ticketrecharge.TicketRechargeQuoteResponse;
import com.transport.simulator.service.TicketRechargeLookupService;
import com.transport.simulator.service.TicketRechargeQuoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/v1/ticket-recharges")
public class PublicTicketRechargeController {

    private final TicketRechargeLookupService lookupService;
    private final TicketRechargeQuoteService quoteService;

    public PublicTicketRechargeController(
            TicketRechargeLookupService lookupService,
            TicketRechargeQuoteService quoteService
    ) {
        this.lookupService = lookupService;
        this.quoteService = quoteService;
    }

    @PostMapping("/lookup")
    public TicketRechargeLookupResponse lookup(
            @Valid @RequestBody TicketRechargeLookupRequest request
    ) {
        return lookupService.findRechargeableTicket(request.qrValue());
    }

    @PostMapping("/quotes")
    public TicketRechargeQuoteResponse quote(
            @Valid @RequestBody TicketRechargeQuoteRequest request
    ) {
        return quoteService.quote(request);
    }
}
