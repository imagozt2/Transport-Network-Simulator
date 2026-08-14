package com.transport.simulator.service;

import com.transport.simulator.dto.request.operator.OperatorDisplayPreferencesRequest;
import com.transport.simulator.dto.response.operator.OperatorDisplayPreferencesResponse;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.OperatorDisplayPreferences;
import com.transport.simulator.repository.OperatorAccountRepository;
import com.transport.simulator.repository.OperatorDisplayPreferencesRepository;
import com.transport.simulator.security.OperatorPrincipal;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OperatorDisplayPreferencesService {

    private final OperatorAccountRepository operatorAccountRepository;
    private final OperatorDisplayPreferencesRepository preferencesRepository;

    public OperatorDisplayPreferencesService(
            OperatorAccountRepository operatorAccountRepository,
            OperatorDisplayPreferencesRepository preferencesRepository
    ) {
        this.operatorAccountRepository = operatorAccountRepository;
        this.preferencesRepository = preferencesRepository;
    }

    @Transactional
    public OperatorDisplayPreferencesResponse getPreferences(Authentication authentication) {
        OperatorPrincipal principal = requirePrincipal(authentication);
        return OperatorDisplayPreferencesResponse.from(findOrCreate(principal.id()));
    }

    @Transactional
    public OperatorDisplayPreferencesResponse updatePreferences(
            Authentication authentication,
            OperatorDisplayPreferencesRequest request
    ) {
        OperatorPrincipal principal = requirePrincipal(authentication);
        String timeZone = validateTimeZone(request.timeZone());
        OperatorDisplayPreferences preferences = findOrCreate(principal.id());
        preferences.update(timeZone, request.theme());
        return OperatorDisplayPreferencesResponse.from(preferencesRepository.save(preferences));
    }

    private OperatorDisplayPreferences findOrCreate(Long operatorId) {
        return preferencesRepository.findById(operatorId).orElseGet(() -> {
            OperatorAccount operator = operatorAccountRepository.findById(operatorId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "Authenticated operator no longer exists"
                    ));
            return preferencesRepository.save(new OperatorDisplayPreferences(operator));
        });
    }

    private String validateTimeZone(String value) {
        String timeZone = value.trim();
        try {
            ZoneId.of(timeZone);
            return timeZone;
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unknown operator time zone"
            );
        }
    }

    private OperatorPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OperatorPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return principal;
    }
}
