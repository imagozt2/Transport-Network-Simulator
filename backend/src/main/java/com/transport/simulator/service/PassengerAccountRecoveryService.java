package com.transport.simulator.service;

import com.transport.simulator.dto.request.passenger.PassengerPasswordResetRequest;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.PassengerAccountToken;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.enums.PassengerAccountTokenType;
import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.PassengerAccountTokenRepository;
import com.transport.simulator.repository.PassengerSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PassengerAccountRecoveryService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PassengerAccountRepository accountRepository;
    private final PassengerAccountTokenRepository tokenRepository;
    private final PassengerSessionRepository sessionRepository;
    private final PassengerAccountTokenDelivery tokenDelivery;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration verificationLifetime;
    private final Duration passwordResetLifetime;

    public PassengerAccountRecoveryService(
            PassengerAccountRepository accountRepository,
            PassengerAccountTokenRepository tokenRepository,
            PassengerSessionRepository sessionRepository,
            PassengerAccountTokenDelivery tokenDelivery,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.rmm-app.account.email-verification-lifetime}") Duration verificationLifetime,
            @Value("${app.rmm-app.account.password-reset-lifetime}") Duration passwordResetLifetime
    ) {
        this.accountRepository = accountRepository;
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
        this.tokenDelivery = tokenDelivery;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.verificationLifetime = verificationLifetime;
        this.passwordResetLifetime = passwordResetLifetime;
    }

    @Transactional
    public void issueEmailVerification(PassengerAccount account) {
        if (account.getEmailVerifiedAt() != null
                || account.getStatus() != PassengerAccountStatus.PENDING_VERIFICATION) {
            return;
        }
        issue(account, PassengerAccountTokenType.EMAIL_VERIFICATION, verificationLifetime);
    }

    @Transactional
    public void requestEmailVerification(String email) {
        accountRepository.findByEmailIgnoreCaseForUpdate(normalizeEmail(email))
                .ifPresent(this::issueEmailVerification);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        PassengerAccountToken token = requiredUsableToken(
                rawToken,
                PassengerAccountTokenType.EMAIL_VERIFICATION
        );
        LocalDateTime now = now();
        token.consume(now);
        token.getPassengerAccount().verifyEmail(now);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        accountRepository.findByEmailIgnoreCaseForUpdate(normalizeEmail(email))
                .filter(account -> account.getStatus() != PassengerAccountStatus.DISABLED)
                .ifPresent(account -> issue(
                        account,
                        PassengerAccountTokenType.PASSWORD_RESET,
                        passwordResetLifetime
                ));
    }

    @Transactional
    public void resetPassword(PassengerPasswordResetRequest request) {
        validatePassword(request.newPassword());
        PassengerAccountToken token = requiredUsableToken(
                request.resetToken(),
                PassengerAccountTokenType.PASSWORD_RESET
        );
        LocalDateTime now = now();
        PassengerAccount account = token.getPassengerAccount();
        token.consume(now);
        account.changePassword(passwordEncoder.encode(request.newPassword()), now);
        sessionRepository.revokeAllActiveByAccountId(account.getId(), now, "PASSWORD_RESET");
    }

    private void issue(
            PassengerAccount account,
            PassengerAccountTokenType type,
            Duration lifetime
    ) {
        tokenRepository.deleteUnusedByAccountAndType(account, type);
        String rawToken = randomToken();
        tokenRepository.save(new PassengerAccountToken(
                account,
                type,
                hash(rawToken),
                now().plus(lifetime)
        ));
        tokenDelivery.deliver(account, type, rawToken);
    }

    private PassengerAccountToken requiredUsableToken(
            String rawToken,
            PassengerAccountTokenType type
    ) {
        PassengerAccountToken token = tokenRepository.findForUse(hash(rawToken), type)
                .orElseThrow(this::invalidToken);
        if (!token.canBeUsedAt(now())) {
            throw invalidToken();
        }
        return token;
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

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Passenger account token could not be hashed", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired account token");
    }
}
