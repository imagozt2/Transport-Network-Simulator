package com.transport.simulator.service;

import com.transport.simulator.dto.request.passenger.PassengerAccountStatusUpdateRequest;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PassengerAccountManagementService {

    private final PassengerAccountRepository passengerAccountRepository;
    private final PassengerAccountStatusChangeRepository statusChangeRepository;
    private final OperatorAccountRepository operatorAccountRepository;

    public PassengerAccountManagementService(
            PassengerAccountRepository passengerAccountRepository,
            PassengerAccountStatusChangeRepository statusChangeRepository,
            OperatorAccountRepository operatorAccountRepository
    ) {
        this.passengerAccountRepository = passengerAccountRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.operatorAccountRepository = operatorAccountRepository;
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
