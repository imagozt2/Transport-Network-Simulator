package com.transport.simulator.controller;

import com.transport.simulator.dto.response.passenger.PassengerRegistrationUserResponse;
import com.transport.simulator.dto.response.passenger.PassengerSessionSummaryResponse;
import com.transport.simulator.service.PassengerResourceAccessService;
import com.transport.simulator.service.PassengerSessionService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rmm-app/v1/me")
public class PassengerAccountController {

    private final PassengerResourceAccessService accessService;
    private final PassengerSessionService sessionService;

    public PassengerAccountController(
            PassengerResourceAccessService accessService,
            PassengerSessionService sessionService
    ) {
        this.accessService = accessService;
        this.sessionService = sessionService;
    }

    @GetMapping
    public PassengerRegistrationUserResponse currentAccount(Authentication authentication) {
        return PassengerRegistrationUserResponse.from(accessService.currentAccount(authentication));
    }

    @GetMapping("/sessions")
    public List<PassengerSessionSummaryResponse> sessions(Authentication authentication) {
        return sessionService.sessions(authentication);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(
            @PathVariable String sessionId,
            Authentication authentication
    ) {
        sessionService.revokeSession(sessionId, authentication);
    }
}
