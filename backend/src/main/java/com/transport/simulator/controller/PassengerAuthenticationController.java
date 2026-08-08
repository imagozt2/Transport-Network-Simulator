package com.transport.simulator.controller;

import com.transport.simulator.dto.request.passenger.PassengerRegistrationRequest;
import com.transport.simulator.dto.request.passenger.PassengerLoginRequest;
import com.transport.simulator.dto.request.passenger.PassengerSessionRefreshRequest;
import com.transport.simulator.dto.response.passenger.PassengerRegistrationResponse;
import com.transport.simulator.dto.response.passenger.PassengerSessionResponse;
import com.transport.simulator.service.PassengerRegistrationService;
import com.transport.simulator.service.PassengerSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/rmm-app/v1/auth")
public class PassengerAuthenticationController {

    private final PassengerRegistrationService registrationService;
    private final PassengerSessionService sessionService;

    public PassengerAuthenticationController(
            PassengerRegistrationService registrationService,
            PassengerSessionService sessionService
    ) {
        this.registrationService = registrationService;
        this.sessionService = sessionService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public PassengerRegistrationResponse register(
            @Valid @RequestBody PassengerRegistrationRequest request
    ) {
        return registrationService.register(request);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public PassengerSessionResponse login(@Valid @RequestBody PassengerLoginRequest request) {
        return sessionService.login(request);
    }

    @PostMapping("/session-refreshes")
    public PassengerSessionResponse refresh(
            @Valid @RequestBody PassengerSessionRefreshRequest request
    ) {
        return sessionService.refresh(request);
    }

    @DeleteMapping("/sessions/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {
        sessionService.logout(authentication);
    }
}
