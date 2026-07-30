package com.transport.simulator.service;

import com.transport.simulator.dto.request.auth.OperatorLoginRequest;
import com.transport.simulator.dto.response.auth.OperatorAccountResponse;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.enums.OperatorAccountStatus;
import com.transport.simulator.repository.OperatorAccountRepository;
import com.transport.simulator.security.OperatorPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OperatorAuthenticationService {

    private static final int MAXIMUM_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final OperatorAccountRepository operatorAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock serviceClock;
    private final String dummyPasswordHash;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public OperatorAuthenticationService(
            OperatorAccountRepository operatorAccountRepository,
            PasswordEncoder passwordEncoder,
            Clock serviceClock
    ) {
        this.operatorAccountRepository = operatorAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.serviceClock = serviceClock;
        this.dummyPasswordHash = passwordEncoder.encode("non-existent-operator-account");
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public OperatorAccountResponse login(
            OperatorLoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        String identifier = request.identifier().trim();
        LocalDateTime now = LocalDateTime.now(serviceClock);
        OperatorAccount account = operatorAccountRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .orElse(null);

        if (account == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }

        account.releaseExpiredLock(now);
        if (account.getStatus() == OperatorAccountStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operator account is disabled");
        }
        if (account.getStatus() == OperatorAccountStatus.LOCKED) {
            throw new ResponseStatusException(
                    HttpStatus.LOCKED,
                    "Operator account is temporarily locked"
            );
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            account.registerFailedLogin(now, MAXIMUM_FAILED_ATTEMPTS, LOCK_MINUTES);
            operatorAccountRepository.save(account);
            throw invalidCredentials();
        }

        account.registerSuccessfulLogin(now);
        operatorAccountRepository.save(account);
        establishSession(account, servletRequest, servletResponse);
        return OperatorAccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public OperatorAccountResponse currentOperator(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OperatorPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }

        return operatorAccountRepository.findById(principal.id())
                .map(OperatorAccountResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated operator no longer exists"
                ));
    }

    public void logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    private void establishSession(
            OperatorAccount account,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        OperatorPrincipal principal = OperatorPrincipal.from(account);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid operator credentials");
    }
}
