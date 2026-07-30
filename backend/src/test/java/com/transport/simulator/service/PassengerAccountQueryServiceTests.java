package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.repository.PassengerAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PassengerAccountQueryServiceTests {

    @Mock
    private PassengerAccountRepository repository;

    private PassengerAccountQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new PassengerAccountQueryService(repository);
    }

    @Test
    void shouldNormalizeFiltersClampPaginationAndKeepAGlobalSummary() {
        PassengerAccount passenger = passenger();
        when(repository.findAdministrativePage(
                eq("%ana%"), eq(PassengerAccountStatus.ACTIVE), eq(true), any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(passenger),
                PageRequest.of(0, 100),
                1
        ));
        when(repository.count()).thenReturn(12L);
        when(repository.countByStatus(PassengerAccountStatus.ACTIVE)).thenReturn(8L);
        when(repository.countByStatus(PassengerAccountStatus.BLOCKED)).thenReturn(2L);
        when(repository.countByStatus(PassengerAccountStatus.DISABLED)).thenReturn(2L);
        when(repository.countByEmailVerifiedAtIsNull()).thenReturn(3L);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        var response = queryService.getAccounts(
                -4, 500, "  ANA ", PassengerAccountStatus.ACTIVE, true,
                "name", "ASC"
        );

        verify(repository).findAdministrativePage(
                eq("%ana%"), eq(PassengerAccountStatus.ACTIVE), eq(true), pageable.capture()
        );
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("lastName").isAscending()).isTrue();
        assertThat(response.users()).singleElement().satisfies(user -> {
            assertThat(user.publicId()).isEqualTo("7dfd4685-8da2-4b9f-bf16-f641411ab174");
            assertThat(user.email()).isEqualTo("ana@example.local");
            assertThat(user.emailVerified()).isTrue();
        });
        assertThat(response.summary().totalAccounts()).isEqualTo(12);
        assertThat(response.summary().pendingVerificationAccounts()).isEqualTo(3);
    }

    @Test
    void shouldReturnNotFoundForAnUnknownPublicIdAndRejectUnsafeSorting() {
        when(repository.findByPublicId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getAccount(" unknown "))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> queryService.getAccounts(
                0, 20, null, null, null, "passwordHash", "ASC"
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private PassengerAccount passenger() {
        PassengerAccount passenger = mock(PassengerAccount.class);
        when(passenger.getPublicId()).thenReturn("7dfd4685-8da2-4b9f-bf16-f641411ab174");
        when(passenger.getEmail()).thenReturn("ana@example.local");
        when(passenger.getFirstName()).thenReturn("Ana");
        when(passenger.getLastName()).thenReturn("García");
        when(passenger.getStatus()).thenReturn(PassengerAccountStatus.ACTIVE);
        when(passenger.getEmailVerifiedAt()).thenReturn(LocalDateTime.of(2026, 7, 20, 10, 0));
        when(passenger.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 7, 19, 10, 0));
        when(passenger.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 7, 20, 10, 0));
        return passenger;
    }
}
