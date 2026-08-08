package com.transport.simulator.controller;

import com.transport.simulator.dto.request.passenger.PassengerRegistrationRequest;
import com.transport.simulator.dto.request.passenger.PassengerLoginRequest;
import com.transport.simulator.dto.request.passenger.PassengerSessionRefreshRequest;
import com.transport.simulator.dto.request.passenger.PassengerEmailRequest;
import com.transport.simulator.dto.request.passenger.PassengerEmailVerificationRequest;
import com.transport.simulator.dto.request.passenger.PassengerPasswordResetRequest;
import com.transport.simulator.dto.response.passenger.PassengerRegistrationResponse;
import com.transport.simulator.dto.response.passenger.PassengerSessionResponse;
import com.transport.simulator.service.PassengerRegistrationService;
import com.transport.simulator.service.PassengerSessionService;
import com.transport.simulator.service.PassengerAccountRecoveryService;
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
    private final PassengerAccountRecoveryService recoveryService;

    public PassengerAuthenticationController(
            PassengerRegistrationService registrationService,
            PassengerSessionService sessionService,
            PassengerAccountRecoveryService recoveryService
    ) {
        this.registrationService = registrationService;
        this.sessionService = sessionService;
        this.recoveryService = recoveryService;
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

    @PostMapping("/email-verification-requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestEmailVerification(@Valid @RequestBody PassengerEmailRequest request) {
        recoveryService.requestEmailVerification(request.email());
    }

    @PostMapping("/email-verifications")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody PassengerEmailVerificationRequest request) {
        recoveryService.verifyEmail(request.verificationToken());
    }

    @PostMapping("/password-recovery-requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordRecovery(@Valid @RequestBody PassengerEmailRequest request) {
        recoveryService.requestPasswordReset(request.email());
    }

    @PostMapping("/password-resets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PassengerPasswordResetRequest request) {
        recoveryService.resetPassword(request);
    }
}
