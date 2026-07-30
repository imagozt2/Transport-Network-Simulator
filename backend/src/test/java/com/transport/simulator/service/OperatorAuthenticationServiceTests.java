package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.request.auth.OperatorLoginRequest;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.enums.OperatorAccountStatus;
import com.transport.simulator.enums.OperatorRole;
import com.transport.simulator.repository.OperatorAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OperatorAuthenticationServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T08:00:00Z"),
            ZoneId.of("Europe/Madrid")
    );

    @Mock
    private OperatorAccountRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private OperatorAuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("non-existent-operator-account")).thenReturn("dummy-hash");
        authenticationService = new OperatorAuthenticationService(
                repository,
                passwordEncoder,
                CLOCK
        );
    }

    @Test
    void shouldAuthenticateAnActiveOperatorAndCreateTheSession() {
        OperatorAccount account = account();
        when(repository.findByUsernameIgnoreCaseOrEmailIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = authenticationService.login(
                new OperatorLoginRequest(" admin ", "correct-password"),
                request,
                new MockHttpServletResponse()
        );

        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.role()).isEqualTo(OperatorRole.ADMINISTRATOR);
        assertThat(account.getLastLoginAt()).isNotNull();
        assertThat(request.getSession(false)).isNotNull();
        verify(repository).save(account);
    }

    @Test
    void shouldRejectUnknownCredentialsWithoutRevealingTheIdentifier() {
        when(repository.findByUsernameIgnoreCaseOrEmailIgnoreCase("unknown", "unknown"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.matches("wrong-password", "dummy-hash")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.login(
                new OperatorLoginRequest("unknown", "wrong-password"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(passwordEncoder).matches("wrong-password", "dummy-hash");
    }

    @Test
    void shouldLockAnAccountAfterFiveFailedAttempts() {
        OperatorAccount account = account();
        when(repository.findByUsernameIgnoreCaseOrEmailIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> authenticationService.login(
                    new OperatorLoginRequest("admin", "wrong-password"),
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse()
            )).isInstanceOf(ResponseStatusException.class);
        }

        assertThat(account.getStatus()).isEqualTo(OperatorAccountStatus.LOCKED);
        assertThat(account.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(account.getLockedUntil()).isEqualTo(
                LocalDateTime.of(2026, 7, 30, 10, 15)
        );
        verify(repository, org.mockito.Mockito.times(5)).save(account);
    }

    private OperatorAccount account() {
        return new OperatorAccount(
                "admin",
                "admin@macegocia.local",
                "stored-hash",
                "Ivan",
                "Administrador",
                OperatorRole.ADMINISTRATOR
        );
    }
}
