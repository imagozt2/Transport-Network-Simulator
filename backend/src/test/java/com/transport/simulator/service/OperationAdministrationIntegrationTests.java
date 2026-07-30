package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.request.auth.OperatorLoginRequest;
import com.transport.simulator.dto.request.passenger.PassengerAccountStatusUpdateRequest;
import com.transport.simulator.dto.response.auth.OperatorAccountResponse;
import com.transport.simulator.dto.response.passenger.PassengerAccountResponse;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.PassengerAccountStatusChange;
import com.transport.simulator.enums.OperatorAccountStatus;
import com.transport.simulator.enums.OperatorRole;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.repository.OperatorAccountRepository;
import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.PassengerAccountStatusChangeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperationAdministrationIntegrationTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T08:00:00Z"),
            ZoneId.of("Europe/Madrid")
    );

    @Mock private OperatorAccountRepository operatorRepository;
    @Mock private PassengerAccountRepository passengerRepository;
    @Mock private PassengerAccountStatusChangeRepository statusChangeRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private OperatorAuthenticationService authenticationService;
    private PassengerAccountQueryService queryService;
    private PassengerAccountManagementService managementService;
    private OperatorAccount administrator;
    private PassengerAccount passenger;
    private AtomicReference<PassengerAccountStatus> passengerStatus;

    @BeforeEach
    void setUp() {
        administrator = administrator();
        passengerStatus = new AtomicReference<>(PassengerAccountStatus.ACTIVE);
        passenger = passenger(passengerStatus);

        when(passwordEncoder.encode("non-existent-operator-account")).thenReturn("dummy-hash");
        authenticationService = new OperatorAuthenticationService(
                operatorRepository, passwordEncoder, CLOCK
        );
        queryService = new PassengerAccountQueryService(passengerRepository);
        managementService = new PassengerAccountManagementService(
                passengerRepository, statusChangeRepository, operatorRepository
        );

        when(operatorRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("admin", "admin"))
                .thenReturn(Optional.of(administrator));
        when(operatorRepository.findById(7L)).thenReturn(Optional.of(administrator));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
        when(passengerRepository.findByPublicId("passenger-uuid"))
                .thenReturn(Optional.of(passenger));
        when(passengerRepository.findAdministrativePage(
                any(), any(), any(), any(Pageable.class)
        )).thenAnswer(invocation -> {
            PassengerAccountStatus requestedStatus = invocation.getArgument(1);
            List<PassengerAccount> content =
                    requestedStatus == null || requestedStatus == passengerStatus.get()
                            ? List.of(passenger)
                            : List.of();
            return new PageImpl<>(content, invocation.getArgument(3), content.size());
        });
        when(passengerRepository.count()).thenReturn(1L);
        when(passengerRepository.countByStatus(any())).thenAnswer(invocation ->
                invocation.getArgument(0) == passengerStatus.get() ? 1L : 0L
        );
        when(passengerRepository.countByEmailVerifiedAtIsNull()).thenReturn(0L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateQueryManageAuditAndQueryThePassengerAgain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        authenticationService.login(
                new OperatorLoginRequest("admin", "correct-password"),
                request,
                new MockHttpServletResponse()
        );
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        var initialPage = queryService.getAccounts(
                0, 20, null, PassengerAccountStatus.ACTIVE, true,
                "registeredAt", "DESC"
        );
        assertThat(initialPage.users()).singleElement()
                .extracting(PassengerAccountResponse::status)
                .isEqualTo(PassengerAccountStatus.ACTIVE);

        var updated = managementService.updateStatus(
                "passenger-uuid",
                new PassengerAccountStatusUpdateRequest(
                        PassengerAccountStatus.BLOCKED,
                        "Actividad incompatible con las condiciones"
                ),
                authentication
        );

        ArgumentCaptor<PassengerAccountStatusChange> audit =
                ArgumentCaptor.forClass(PassengerAccountStatusChange.class);
        verify(statusChangeRepository).save(audit.capture());
        assertThat(updated.status()).isEqualTo(PassengerAccountStatus.BLOCKED);
        assertThat(audit.getValue().getPreviousStatus()).isEqualTo(PassengerAccountStatus.ACTIVE);
        assertThat(audit.getValue().getNewStatus()).isEqualTo(PassengerAccountStatus.BLOCKED);
        assertThat(audit.getValue().getChangedByOperator()).isSameAs(administrator);

        var blockedPage = queryService.getAccounts(
                0, 20, null, PassengerAccountStatus.BLOCKED, null,
                "registeredAt", "DESC"
        );
        assertThat(blockedPage.summary().blockedAccounts()).isEqualTo(1);
        assertThat(blockedPage.users()).singleElement()
                .extracting(PassengerAccountResponse::status)
                .isEqualTo(PassengerAccountStatus.BLOCKED);
    }

    @Test
    void shouldKeepSensitiveFieldsOutOfAdministrativeContractsAndRejectOperators() {
        assertThat(componentNames(PassengerAccountResponse.class))
                .doesNotContain(
                        "id", "passwordHash", "failedLoginAttempts",
                        "lockedUntil", "passwordChangedAt"
                );
        assertThat(componentNames(OperatorAccountResponse.class))
                .doesNotContain("password", "passwordHash");

        Authentication operatorAuthentication =
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                        .authenticated(
                                new com.transport.simulator.security.OperatorPrincipal(
                                        8L, "operator", "operator@example.local",
                                        "Regular", "Operator", OperatorRole.OPERATOR
                                ),
                                null,
                                List.of()
                        );
        assertThatThrownBy(() -> managementService.updateStatus(
                "passenger-uuid",
                new PassengerAccountStatusUpdateRequest(PassengerAccountStatus.DISABLED, "Motivo"),
                operatorAuthentication
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private List<String> componentNames(Class<? extends Record> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
    }

    private OperatorAccount administrator() {
        OperatorAccount account = mock(OperatorAccount.class);
        when(account.getId()).thenReturn(7L);
        when(account.getUsername()).thenReturn("admin");
        when(account.getEmail()).thenReturn("admin@macegocia.local");
        when(account.getPasswordHash()).thenReturn("stored-hash");
        when(account.getFirstName()).thenReturn("Admin");
        when(account.getLastName()).thenReturn("RMM");
        when(account.getRole()).thenReturn(OperatorRole.ADMINISTRATOR);
        when(account.getStatus()).thenReturn(OperatorAccountStatus.ACTIVE);
        return account;
    }

    private PassengerAccount passenger(
            AtomicReference<PassengerAccountStatus> status
    ) {
        PassengerAccount account = mock(PassengerAccount.class);
        when(account.getPublicId()).thenReturn("passenger-uuid");
        when(account.getEmail()).thenReturn("ana@example.local");
        when(account.getFirstName()).thenReturn("Ana");
        when(account.getLastName()).thenReturn("García");
        when(account.getStatus()).thenAnswer(invocation -> status.get());
        when(account.getEmailVerifiedAt()).thenReturn(LocalDateTime.of(2026, 7, 20, 10, 0));
        when(account.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 7, 19, 10, 0));
        when(account.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 7, 20, 10, 0));
        when(account.changeStatus(any())).thenAnswer(invocation -> {
            PassengerAccountStatus previous = status.get();
            status.set(invocation.getArgument(0));
            return previous;
        });
        return account;
    }
}
