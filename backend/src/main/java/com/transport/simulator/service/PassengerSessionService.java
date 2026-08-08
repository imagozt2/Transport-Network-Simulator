package com.transport.simulator.service;

import com.transport.simulator.dto.request.passenger.PassengerLoginRequest;
import com.transport.simulator.dto.request.passenger.PassengerSessionRefreshRequest;
import com.transport.simulator.dto.response.passenger.PassengerRegistrationUserResponse;
import com.transport.simulator.dto.response.passenger.PassengerSessionResponse;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.PassengerSession;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.PassengerSessionRepository;
import com.transport.simulator.security.PassengerPrincipal;
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
import java.util.List;
import com.transport.simulator.dto.response.passenger.PassengerSessionSummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PassengerSessionService {

    private static final int MAXIMUM_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PassengerAccountRepository accountRepository;
    private final PassengerSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration accessTokenLifetime;
    private final Duration refreshTokenLifetime;
    private final String dummyPasswordHash;

    public PassengerSessionService(
            PassengerAccountRepository accountRepository,
            PassengerSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.rmm-app.session.access-token-lifetime}") Duration accessTokenLifetime,
            @Value("${app.rmm-app.session.refresh-token-lifetime}") Duration refreshTokenLifetime
    ) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.accessTokenLifetime = accessTokenLifetime;
        this.refreshTokenLifetime = refreshTokenLifetime;
        this.dummyPasswordHash = passwordEncoder.encode("non-existent-passenger-account");
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public PassengerSessionResponse login(PassengerLoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        PassengerAccount account = accountRepository.findByEmailIgnoreCaseForUpdate(email)
                .orElse(null);
        if (account == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }
        LocalDateTime now = now();
        if (account.isTemporarilyLocked(now)) {
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            account.registerFailedLogin(now, MAXIMUM_FAILED_ATTEMPTS, LOCK_MINUTES);
            accountRepository.save(account);
            throw invalidCredentials();
        }
        requireActive(account);
        account.registerSuccessfulLogin(now);
        accountRepository.save(account);

        TokenPair tokens = newTokenPair(now);
        PassengerSession session = sessionRepository.save(new PassengerSession(
                account,
                request.device().installationId(),
                request.device().name(),
                request.device().platform(),
                hash(tokens.accessToken()),
                hash(tokens.refreshToken()),
                tokens.accessExpiresAt(),
                tokens.refreshExpiresAt(),
                now
        ));
        return response(tokens, account);
    }

    @Transactional
    public PassengerSessionResponse refresh(PassengerSessionRefreshRequest request) {
        PassengerSession session = sessionRepository
                .findByRefreshTokenHashForUpdate(hash(request.refreshToken()))
                .orElseThrow(this::invalidSession);
        LocalDateTime now = now();
        if (!session.canRefresh(now, request.installationId())) {
            throw invalidSession();
        }
        requireActive(session.getPassengerAccount());

        TokenPair tokens = newTokenPair(now);
        session.rotate(
                hash(tokens.accessToken()),
                hash(tokens.refreshToken()),
                tokens.accessExpiresAt(),
                tokens.refreshExpiresAt(),
                now
        );
        return response(tokens, session.getPassengerAccount());
    }

    @Transactional
    public void logout(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof PassengerPrincipal principal)) {
            throw invalidSession();
        }
        PassengerSession session = sessionRepository.findByIdForUpdate(principal.sessionId())
                .orElseThrow(this::invalidSession);
        if (!session.getPassengerAccount().getId().equals(principal.accountId())) {
            throw invalidSession();
        }
        session.revoke(now(), "USER_LOGOUT");
    }

    @Transactional(readOnly = true)
    public List<PassengerSessionSummaryResponse> sessions(Authentication authentication) {
        PassengerPrincipal principal = requiredPrincipal(authentication);
        return sessionRepository
                .findAllByPassengerAccountIdAndRevokedAtIsNullOrderByLastUsedAtDesc(
                        principal.accountId()
                )
                .stream()
                .map(session -> PassengerSessionSummaryResponse.from(
                        session,
                        principal.sessionId()
                ))
                .toList();
    }

    @Transactional
    public void revokeSession(String publicSessionId, Authentication authentication) {
        PassengerPrincipal principal = requiredPrincipal(authentication);
        PassengerSession session = sessionRepository.findOwnedByPublicIdForUpdate(
                publicSessionId == null ? "" : publicSessionId.trim(),
                principal.accountId()
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Passenger session not found"
        ));
        session.revoke(now(), "USER_REVOKED");
    }

    private void requireActive(PassengerAccount account) {
        if (account.getStatus() != PassengerAccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Passenger account is not active");
        }
    }

    private TokenPair newTokenPair(LocalDateTime now) {
        return new TokenPair(
                randomToken(),
                randomToken(),
                now.plus(accessTokenLifetime),
                now.plus(refreshTokenLifetime)
        );
    }

    private PassengerSessionResponse response(TokenPair tokens, PassengerAccount account) {
        return new PassengerSessionResponse(
                tokens.accessToken(),
                tokens.accessExpiresAt().toInstant(ZoneOffset.UTC),
                tokens.refreshToken(),
                tokens.refreshExpiresAt().toInstant(ZoneOffset.UTC),
                PassengerRegistrationUserResponse.from(account)
        );
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Passenger session token could not be hashed", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid passenger credentials");
    }

    private ResponseStatusException invalidSession() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid passenger session");
    }

    private PassengerPrincipal requiredPrincipal(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PassengerPrincipal principal)) {
            throw invalidSession();
        }
        return principal;
    }

    private record TokenPair(
            String accessToken,
            String refreshToken,
            LocalDateTime accessExpiresAt,
            LocalDateTime refreshExpiresAt
    ) {
    }
}
