package com.transport.simulator.controller;

import com.transport.simulator.dto.request.operator.OperatorDisplayPreferencesRequest;
import com.transport.simulator.dto.response.operator.OperatorDisplayPreferencesResponse;
import com.transport.simulator.service.OperatorDisplayPreferencesService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operators/me/display-preferences")
public class OperatorDisplayPreferencesController {

    private final OperatorDisplayPreferencesService preferencesService;

    public OperatorDisplayPreferencesController(
            OperatorDisplayPreferencesService preferencesService
    ) {
        this.preferencesService = preferencesService;
    }

    @GetMapping
    public OperatorDisplayPreferencesResponse getPreferences(Authentication authentication) {
        return preferencesService.getPreferences(authentication);
    }

    @PutMapping
    public OperatorDisplayPreferencesResponse updatePreferences(
            Authentication authentication,
            @Valid @RequestBody OperatorDisplayPreferencesRequest request
    ) {
        return preferencesService.updatePreferences(authentication, request);
    }
}
