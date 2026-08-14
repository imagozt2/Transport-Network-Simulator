package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.request.operator.OperatorDisplayPreferencesRequest;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.OperatorDisplayPreferences;
import com.transport.simulator.enums.OperatorRole;
import com.transport.simulator.enums.OperatorTheme;
import com.transport.simulator.repository.OperatorAccountRepository;
import com.transport.simulator.repository.OperatorDisplayPreferencesRepository;
import com.transport.simulator.security.OperatorPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OperatorDisplayPreferencesServiceTests {

    private static final long OPERATOR_ID = 7L;

    @Mock
    private OperatorAccountRepository operatorAccountRepository;

    @Mock
    private OperatorDisplayPreferencesRepository preferencesRepository;

    private OperatorDisplayPreferencesService service;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        service = new OperatorDisplayPreferencesService(
                operatorAccountRepository,
                preferencesRepository
        );
        OperatorPrincipal principal = new OperatorPrincipal(
                OPERATOR_ID,
                "admin",
                "admin@macegocia.local",
                "Ivan",
                "Administrador",
                OperatorRole.ADMINISTRATOR
        );
        authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void shouldCreateAndPersistDefaultPreferencesForANewOperator() {
        OperatorAccount operator = org.mockito.Mockito.mock(OperatorAccount.class);
        when(preferencesRepository.findById(OPERATOR_ID)).thenReturn(Optional.empty());
        when(operatorAccountRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator));
        when(preferencesRepository.save(any(OperatorDisplayPreferences.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.getPreferences(authentication);

        assertThat(response.timeZone()).isEqualTo("Europe/Madrid");
        assertThat(response.theme()).isEqualTo(OperatorTheme.LIGHT);
        verify(preferencesRepository).save(any(OperatorDisplayPreferences.class));
    }

    @Test
    void shouldPersistTheSelectedTimeZoneAndTheme() {
        OperatorDisplayPreferences preferences = new OperatorDisplayPreferences(
                org.mockito.Mockito.mock(OperatorAccount.class)
        );
        when(preferencesRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(preferences));
        when(preferencesRepository.save(preferences)).thenReturn(preferences);

        var response = service.updatePreferences(
                authentication,
                new OperatorDisplayPreferencesRequest(" America/New_York ", OperatorTheme.DARK)
        );

        assertThat(response.timeZone()).isEqualTo("America/New_York");
        assertThat(response.theme()).isEqualTo(OperatorTheme.DARK);
        verify(preferencesRepository).save(preferences);
    }

    @Test
    void shouldRejectAnUnknownTimeZoneWithoutChangingPersistedPreferences() {
        assertThatThrownBy(() -> service.updatePreferences(
                authentication,
                new OperatorDisplayPreferencesRequest("Macegocia/Capital", OperatorTheme.DARK)
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void shouldRequireAnAuthenticatedOperator() {
        assertThatThrownBy(() -> service.getPreferences(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
