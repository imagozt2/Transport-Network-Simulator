package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.PurchaseRepository;
import com.transport.simulator.repository.TicketJourneyRepository;
import com.transport.simulator.repository.TicketOperationRepository;
import com.transport.simulator.repository.TicketQrCredentialRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.repository.TicketSupportRepository;
import com.transport.simulator.security.PassengerPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PassengerDataIsolationIntegrationTests {

    @Mock private PassengerAccountRepository accountRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketSupportRepository supportRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private TicketJourneyRepository journeyRepository;
    @Mock private TicketQrCredentialRepository qrCredentialRepository;
    @Mock private TicketOperationRepository operationRepository;

    private PassengerTicketQueryService ticketQueryService;
    private PassengerJourneyHistoryService journeyHistoryService;

    @BeforeEach
    void setUp() {
        PassengerResourceAccessService accessService = new PassengerResourceAccessService(
                accountRepository, ticketRepository, supportRepository, purchaseRepository);
        ticketQueryService = new PassengerTicketQueryService(
                accessService, ticketRepository, supportRepository, journeyRepository,
                qrCredentialRepository, operationRepository);
        journeyHistoryService = new PassengerJourneyHistoryService(accessService, journeyRepository);
    }

    @Test
    void shouldKeepWalletsAndJourneyHistoriesIsolatedBetweenPassengers() {
        Authentication passengerA = passenger(10L, "passenger-a");
        Authentication passengerB = passenger(20L, "passenger-b");
        when(ticketRepository.findPassengerWallet(
                org.mockito.ArgumentMatchers.eq(10L), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class))).thenReturn(List.of());
        when(ticketRepository.findPassengerWallet(
                org.mockito.ArgumentMatchers.eq(20L), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class))).thenReturn(List.of());
        when(journeyRepository.findPassengerJourneyHistory(
                org.mockito.ArgumentMatchers.eq(10L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());
        when(journeyRepository.findPassengerJourneyHistory(
                org.mockito.ArgumentMatchers.eq(20L), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(ticketQueryService.tickets(null, null, 20, null, passengerA).items()).isEmpty();
        assertThat(ticketQueryService.tickets(null, null, 20, null, passengerB).items()).isEmpty();
        assertThat(journeyHistoryService.history(20, null, passengerA).items()).isEmpty();
        assertThat(journeyHistoryService.history(20, null, passengerB).items()).isEmpty();

        verify(ticketRepository).findPassengerWallet(
                org.mockito.ArgumentMatchers.eq(10L), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class));
        verify(ticketRepository).findPassengerWallet(
                org.mockito.ArgumentMatchers.eq(20L), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class));
        verify(journeyRepository).findPassengerJourneyHistory(
                org.mockito.ArgumentMatchers.eq(10L), isNull(), isNull(), any(Pageable.class));
        verify(journeyRepository).findPassengerJourneyHistory(
                org.mockito.ArgumentMatchers.eq(20L), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void shouldNotRevealForeignResourcesThroughDetailsOrPaginationCursors() {
        Authentication passengerA = passenger(10L, "passenger-a");
        when(ticketRepository.findByCodeAndPassengerAccountId("TICKET-B", 10L))
                .thenReturn(Optional.empty());
        when(journeyRepository.findByCodeAndPassengerAccountId("JOURNEY-B", 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketQueryService.ticket("TICKET-B", passengerA))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> journeyHistoryService.history(20, "JOURNEY-B", passengerA))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(supportRepository, never())
                .findFirstByTicketIdAndStatusOrderByIssuedAtDesc(any(), any());
        verify(journeyRepository, never()).findPassengerJourneyHistory(
                any(), any(), any(), any(Pageable.class));
    }

    @Test
    void shouldRejectOperatorCredentialsOnPassengerResources() {
        Authentication operator = new UsernamePasswordAuthenticationToken(
                "control-center-operator", "", List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));

        assertThatThrownBy(() -> ticketQueryService.tickets(null, null, 20, null, operator))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> journeyHistoryService.history(20, null, operator))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(ticketRepository, never()).findPassengerWallet(
                any(), any(), any(), any(), any(), any(Pageable.class));
        verify(journeyRepository, never()).findPassengerJourneyHistory(
                any(), any(), any(), any(Pageable.class));
    }

    private Authentication passenger(Long accountId, String publicId) {
        return new UsernamePasswordAuthenticationToken(
                new PassengerPrincipal(accountId, publicId, accountId + 100L,
                        "installation-" + accountId),
                "",
                List.of(new SimpleGrantedAuthority("ROLE_PASSENGER")));
    }
}
