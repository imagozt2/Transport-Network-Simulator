package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.request.passenger.PassengerAccountStatusUpdateRequest;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.PassengerAccountStatusChange;
import com.transport.simulator.enums.OperatorRole;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.repository.OperatorAccountRepository;
import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.PassengerAccountStatusChangeRepository;
import com.transport.simulator.security.OperatorPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PassengerAccountManagementServiceTests {

    @Mock
    private PassengerAccountRepository passengerRepository;
    @Mock
    private PassengerAccountStatusChangeRepository changeRepository;
    @Mock
    private OperatorAccountRepository operatorRepository;

    private PassengerAccountManagementService managementService;

    @BeforeEach
    void setUp() {
        managementService = new PassengerAccountManagementService(
                passengerRepository, changeRepository, operatorRepository
        );
    }

    @Test
    void shouldPersistTheStateChangeAndItsResponsibleAdministrator() {
        OperatorAccount operator = mock(OperatorAccount.class);
        PassengerAccount passenger = passenger(PassengerAccountStatus.BLOCKED);
        when(operatorRepository.findById(7L)).thenReturn(Optional.of(operator));
        when(passengerRepository.findByPublicId("passenger-uuid"))
                .thenReturn(Optional.of(passenger));
        when(passenger.changeStatus(PassengerAccountStatus.BLOCKED))
                .thenReturn(PassengerAccountStatus.ACTIVE);
        ArgumentCaptor<PassengerAccountStatusChange> change =
                ArgumentCaptor.forClass(PassengerAccountStatusChange.class);

        var response = managementService.updateStatus(
                " passenger-uuid ",
                new PassengerAccountStatusUpdateRequest(
                        PassengerAccountStatus.BLOCKED,
                        "  Incumplimiento de las condiciones  "
                ),
                authentication(OperatorRole.ADMINISTRATOR)
        );

        verify(passengerRepository).save(passenger);
        verify(changeRepository).save(change.capture());
        assertThat(change.getValue().getPassengerAccount()).isSameAs(passenger);
        assertThat(change.getValue().getChangedByOperator()).isSameAs(operator);
        assertThat(change.getValue().getPreviousStatus()).isEqualTo(PassengerAccountStatus.ACTIVE);
        assertThat(change.getValue().getNewStatus()).isEqualTo(PassengerAccountStatus.BLOCKED);
        assertThat(change.getValue().getReason())
                .isEqualTo("Incumplimiento de las condiciones");
        assertThat(response.status()).isEqualTo(PassengerAccountStatus.BLOCKED);
    }

    @Test
    void shouldRejectNonAdministratorsAndMissingRestrictionReasons() {
        assertThatThrownBy(() -> managementService.updateStatus(
                "passenger-uuid",
                new PassengerAccountStatusUpdateRequest(PassengerAccountStatus.ACTIVE, null),
                authentication(OperatorRole.OPERATOR)
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        OperatorAccount operator = mock(OperatorAccount.class);
        PassengerAccount passenger = mock(PassengerAccount.class);
        when(operatorRepository.findById(7L)).thenReturn(Optional.of(operator));
        when(passengerRepository.findByPublicId("passenger-uuid"))
                .thenReturn(Optional.of(passenger));
        assertThatThrownBy(() -> managementService.updateStatus(
                "passenger-uuid",
                new PassengerAccountStatusUpdateRequest(PassengerAccountStatus.DISABLED, " "),
                authentication(OperatorRole.ADMINISTRATOR)
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(changeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Authentication authentication(OperatorRole role) {
        OperatorPrincipal principal = new OperatorPrincipal(
                7L, "operator", "operator@example.local", "Test", "Operator", role
        );
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of());
    }

    private PassengerAccount passenger(PassengerAccountStatus status) {
        PassengerAccount passenger = mock(PassengerAccount.class);
        when(passenger.getPublicId()).thenReturn("passenger-uuid");
        when(passenger.getEmail()).thenReturn("ana@example.local");
        when(passenger.getFirstName()).thenReturn("Ana");
        when(passenger.getLastName()).thenReturn("García");
        when(passenger.getStatus()).thenReturn(status);
        return passenger;
    }
}
