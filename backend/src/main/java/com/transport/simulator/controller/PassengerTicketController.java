package com.transport.simulator.controller;

import com.transport.simulator.dto.response.passengerticket.PassengerTicketDetailResponse;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketsResponse;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketQrResponse;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketHistoryResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import com.transport.simulator.service.PassengerTicketQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rmm-app/v1/tickets")
public class PassengerTicketController {

    private final PassengerTicketQueryService ticketQueryService;

    public PassengerTicketController(PassengerTicketQueryService ticketQueryService) {
        this.ticketQueryService = ticketQueryService;
    }

    @GetMapping
    public PassengerTicketsResponse tickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            Authentication authentication
    ) {
        return ticketQueryService.tickets(status, productType, limit, cursor, authentication);
    }

    @GetMapping("/{ticketCode}")
    public PassengerTicketDetailResponse ticket(
            @PathVariable String ticketCode,
            Authentication authentication
    ) {
        return ticketQueryService.ticket(ticketCode, authentication);
    }

    @GetMapping("/{ticketCode}/qr")
    public ResponseEntity<PassengerTicketQrResponse> ticketQr(
            @PathVariable String ticketCode,
            Authentication authentication
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(ticketQueryService.ticketQr(ticketCode, authentication));
    }

    @GetMapping("/{ticketCode}/history")
    public PassengerTicketHistoryResponse history(
            @PathVariable String ticketCode,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            Authentication authentication
    ) {
        return ticketQueryService.history(ticketCode, limit, cursor, authentication);
    }
}
