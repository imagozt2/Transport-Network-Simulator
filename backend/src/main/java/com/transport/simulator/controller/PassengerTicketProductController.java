package com.transport.simulator.controller;

import com.transport.simulator.dto.response.passengerticket.PassengerTicketProductResponse;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketProductsResponse;
import com.transport.simulator.repository.TicketProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rmm-app/v1/ticket-products")
@Transactional(readOnly = true)
public class PassengerTicketProductController {

    private final TicketProductRepository productRepository;

    public PassengerTicketProductController(TicketProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public PassengerTicketProductsResponse products() {
        return new PassengerTicketProductsResponse(
                productRepository.findAllByOrderByCodeAsc().stream()
                        .filter(product -> product.isActive())
                        .map(PassengerTicketProductResponse::from)
                        .toList()
        );
    }

    @GetMapping("/{productCode}")
    public PassengerTicketProductResponse product(@PathVariable String productCode) {
        return productRepository.findByCodeIgnoreCase(productCode.trim())
                .filter(product -> product.isActive())
                .map(PassengerTicketProductResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Active ticket product not found"
                ));
    }
}
