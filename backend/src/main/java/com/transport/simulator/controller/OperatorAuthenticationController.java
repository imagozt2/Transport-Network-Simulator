package com.transport.simulator.controller;

import com.transport.simulator.dto.request.auth.OperatorLoginRequest;
import com.transport.simulator.dto.response.auth.CsrfTokenResponse;
import com.transport.simulator.dto.response.auth.OperatorAccountResponse;
import com.transport.simulator.service.OperatorAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class OperatorAuthenticationController {

    private final OperatorAuthenticationService authenticationService;

    public OperatorAuthenticationController(OperatorAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public OperatorAccountResponse login(
            @Valid @RequestBody OperatorLoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        return authenticationService.login(request, servletRequest, servletResponse);
    }

    @GetMapping("/me")
    public OperatorAccountResponse currentOperator(Authentication authentication) {
        return authenticationService.currentOperator(authentication);
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken()
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authenticationService.logout(authentication, request, response);
    }
}
