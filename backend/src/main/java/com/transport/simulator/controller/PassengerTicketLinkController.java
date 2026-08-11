package com.transport.simulator.controller;

import com.transport.simulator.dto.request.passengerticket.PassengerTicketLinkRequest;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketDetailResponse;
import com.transport.simulator.service.PassengerTicketLinkService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rmm-app/v1/ticket-links")
public class PassengerTicketLinkController {

    private final PassengerTicketLinkService ticketLinkService;

    public PassengerTicketLinkController(PassengerTicketLinkService ticketLinkService) {
        this.ticketLinkService = ticketLinkService;
    }

    @PostMapping
    public ResponseEntity<PassengerTicketDetailResponse> link(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PassengerTicketLinkRequest request,
            Authentication authentication
    ) {
        PassengerTicketDetailResponse response = ticketLinkService.link(
                idempotencyKey, request, authentication
        );
        return ResponseEntity.created(URI.create("/api/rmm-app/v1/tickets/" + response.code()))
                .body(response);
    }
}
