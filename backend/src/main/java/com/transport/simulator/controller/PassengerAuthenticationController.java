package com.transport.simulator.controller;

import com.transport.simulator.dto.request.passenger.PassengerRegistrationRequest;
import com.transport.simulator.dto.response.passenger.PassengerRegistrationResponse;
import com.transport.simulator.service.PassengerRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rmm-app/v1/auth")
public class PassengerAuthenticationController {

    private final PassengerRegistrationService registrationService;

    public PassengerAuthenticationController(PassengerRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public PassengerRegistrationResponse register(
            @Valid @RequestBody PassengerRegistrationRequest request
    ) {
        return registrationService.register(request);
    }
}
