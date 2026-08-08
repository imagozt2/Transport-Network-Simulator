package com.transport.simulator.service;

import com.transport.simulator.dto.request.passenger.PassengerRegistrationRequest;
import com.transport.simulator.dto.response.passenger.PassengerRegistrationResponse;
import com.transport.simulator.dto.response.passenger.PassengerRegistrationUserResponse;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.repository.PassengerAccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PassengerRegistrationService {

    private final PassengerAccountRepository passengerAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String currentTermsVersion;
    private final PassengerAccountRecoveryService recoveryService;

    public PassengerRegistrationService(
            PassengerAccountRepository passengerAccountRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            PassengerAccountRecoveryService recoveryService,
            @Value("${app.rmm-app.current-terms-version}") String currentTermsVersion
    ) {
        this.passengerAccountRepository = passengerAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.recoveryService = recoveryService;
        this.currentTermsVersion = currentTermsVersion;
    }

    @Transactional
    public PassengerRegistrationResponse register(PassengerRegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (passengerAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A passenger account already uses this email address"
            );
        }
        validatePassword(request.password());
        if (!currentTermsVersion.equals(request.termsVersion().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The current terms and conditions must be accepted"
            );
        }

        PassengerAccount passenger = PassengerAccount.register(
                UUID.randomUUID().toString(), email, passwordEncoder.encode(request.password()),
                request.firstName(), request.lastName(), request.locale(), currentTermsVersion,
                LocalDateTime.now(clock)
        );
        PassengerAccount persisted = passengerAccountRepository.save(passenger);
        recoveryService.issueEmailVerification(persisted);
        return new PassengerRegistrationResponse(
                PassengerRegistrationUserResponse.from(persisted), true
        );
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
}
