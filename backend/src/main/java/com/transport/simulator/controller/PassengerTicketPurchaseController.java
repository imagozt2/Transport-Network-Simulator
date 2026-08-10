package com.transport.simulator.controller;

import com.transport.simulator.dto.request.passengerticket.PassengerTicketPurchaseRequest;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketPurchaseResponse;
import com.transport.simulator.service.PassengerTicketPurchaseService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rmm-app/v1/purchases")
public class PassengerTicketPurchaseController {

    private final PassengerTicketPurchaseService purchaseService;

    public PassengerTicketPurchaseController(PassengerTicketPurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public ResponseEntity<PassengerTicketPurchaseResponse> purchase(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PassengerTicketPurchaseRequest request,
            Authentication authentication
    ) {
        PassengerTicketPurchaseResponse response;
        try {
            response = PassengerTicketPurchaseResponse.from(
                    purchaseService.purchase(idempotencyKey, request, authentication)
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        return ResponseEntity.created(URI.create("/api/rmm-app/v1/purchases/" + response.code()))
                .body(response);
    }

    @GetMapping("/{purchaseCode}")
    public PassengerTicketPurchaseResponse purchase(
            @PathVariable String purchaseCode,
            Authentication authentication
    ) {
        return PassengerTicketPurchaseResponse.from(
                purchaseService.ownedPurchase(purchaseCode, authentication)
        );
    }
}
