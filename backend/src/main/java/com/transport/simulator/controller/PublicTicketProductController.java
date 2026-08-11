package com.transport.simulator.controller;

import com.transport.simulator.dto.response.passengerticket.PassengerTicketProductResponse;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketProductsResponse;
import com.transport.simulator.repository.TicketProductRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/v1/ticket-products")
@Transactional(readOnly = true)
public class PublicTicketProductController {

    private final TicketProductRepository productRepository;

    public PublicTicketProductController(TicketProductRepository productRepository) {
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
}
