package com.transport.simulator.controller;

import com.transport.simulator.dto.request.ticketrecharge.TicketRechargeLookupRequest;
import com.transport.simulator.dto.response.ticketrecharge.TicketRechargeLookupResponse;
import com.transport.simulator.service.TicketRechargeLookupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/v1/ticket-recharges")
public class PublicTicketRechargeController {

    private final TicketRechargeLookupService lookupService;

    public PublicTicketRechargeController(TicketRechargeLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @PostMapping("/lookup")
    public TicketRechargeLookupResponse lookup(
            @Valid @RequestBody TicketRechargeLookupRequest request
    ) {
        return lookupService.findRechargeableTicket(request.qrValue());
    }
}
