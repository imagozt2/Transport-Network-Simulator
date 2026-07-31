package com.transport.simulator.service;

import com.transport.simulator.dto.request.passenger.PassengerAccountStatusUpdateRequest;
import com.transport.simulator.dto.request.passenger.PassengerAccountCreateRequest;
import com.transport.simulator.dto.response.passenger.PassengerAccountResponse;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.PassengerAccountStatusChange;
import com.transport.simulator.enums.OperatorRole;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.repository.OperatorAccountRepository;
import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.PassengerAccountStatusChangeRepository;
import com.transport.simulator.security.OperatorPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import java.util.UUID;

@Service
public class PassengerAccountManagementService {

    private final PassengerAccountRepository passengerAccountRepository;
    private final PassengerAccountStatusChangeRepository statusChangeRepository;
    private final OperatorAccountRepository operatorAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public PassengerAccountManagementService(
            PassengerAccountRepository passengerAccountRepository,
            PassengerAccountStatusChangeRepository statusChangeRepository,
            OperatorAccountRepository operatorAccountRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.passengerAccountRepository = passengerAccountRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.operatorAccountRepository = operatorAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    PassengerAccountManagementService(
            PassengerAccountRepository passengerAccountRepository,
            PassengerAccountStatusChangeRepository statusChangeRepository,
            OperatorAccountRepository operatorAccountRepository
    ) {
        this(passengerAccountRepository, statusChangeRepository, operatorAccountRepository, null);
    }

    @Transactional
    public PassengerAccountResponse createAccount(
            PassengerAccountCreateRequest request,
            Authentication authentication
    ) {
        requireAdministrator(authentication);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (passengerAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A passenger account already uses this email address"
            );
        }
        validatePassword(request.password());
        if (passwordEncoder == null) {
            throw new IllegalStateException("Password encoder is not configured");
        }

        PassengerAccount passenger = new PassengerAccount(
                UUID.randomUUID().toString(),
                email,
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName()
        );
        return PassengerAccountResponse.from(passengerAccountRepository.save(passenger));
    }

    @Transactional
    public void deleteAccount(String publicId, Authentication authentication) {
        requireAdministrator(authentication);
        PassengerAccount passenger = passengerAccountRepository
                .findByPublicId(publicId == null ? "" : publicId.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Passenger account not found"
                ));

        statusChangeRepository.deleteAllByPassengerAccountId(passenger.getId());
        passengerAccountRepository.delete(passenger);
    }

    @Transactional
    public PassengerAccountResponse updateStatus(
            String publicId,
            PassengerAccountStatusUpdateRequest request,
            Authentication authentication
    ) {
        OperatorPrincipal principal = requireAdministrator(authentication);
        OperatorAccount operator = operatorAccountRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated operator no longer exists"
                ));
        PassengerAccount passenger = passengerAccountRepository
                .findByPublicId(publicId == null ? "" : publicId.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Passenger account not found"
                ));

        String reason = normalizeReason(request.reason());
        requireReasonForRestrictedStatus(request.status(), reason);

        PassengerAccountStatus previousStatus;
        try {
            previousStatus = passenger.changeStatus(request.status());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }

        passengerAccountRepository.save(passenger);
        statusChangeRepository.save(new PassengerAccountStatusChange(
                passenger,
                operator,
                previousStatus,
                request.status(),
                reason
        ));
        return PassengerAccountResponse.from(passenger);
    }

    private OperatorPrincipal requireAdministrator(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OperatorPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        if (principal.role() != OperatorRole.ADMINISTRATOR) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator role is required"
            );
        }
        return principal;
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private void validatePassword(String password) {
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLowercase || !hasUppercase || !hasDigit) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must contain uppercase, lowercase and numeric characters"
            );
        }
    }

    private void requireReasonForRestrictedStatus(
            PassengerAccountStatus status,
            String reason
    ) {
        if ((status == PassengerAccountStatus.BLOCKED
                || status == PassengerAccountStatus.DISABLED)
                && reason == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A reason is required to block or disable a passenger account"
            );
        }
    }
}
