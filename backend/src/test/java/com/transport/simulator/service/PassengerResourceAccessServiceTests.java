package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.PurchaseRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.repository.TicketSupportRepository;
import com.transport.simulator.security.PassengerPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PassengerResourceAccessServiceTests {
    @Mock PassengerAccountRepository accountRepository;
    @Mock TicketRepository ticketRepository;
    @Mock TicketSupportRepository supportRepository;
    @Mock PurchaseRepository purchaseRepository;
    private PassengerResourceAccessService service;

    @BeforeEach
    void setUp() {
        service = new PassengerResourceAccessService(accountRepository, ticketRepository,
                supportRepository, purchaseRepository);
    }

    @Test
    void shouldResolveTicketsOnlyWithinTheAuthenticatedPassenger() {
        Ticket ownedTicket = org.mockito.Mockito.mock(Ticket.class);
        when(ticketRepository.findByCodeAndPassengerAccountId("TICKET-001", 10L))
                .thenReturn(Optional.of(ownedTicket));

        assertThat(service.ownedTicket(" TICKET-001 ", authentication(10L))).isSameAs(ownedTicket);
        verify(ticketRepository).findByCodeAndPassengerAccountId("TICKET-001", 10L);
    }

    @Test
    void shouldHideResourcesOwnedByAnotherPassenger() {
        when(ticketRepository.findByCodeAndPassengerAccountId("FOREIGN-TICKET", 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ownedTicket("FOREIGN-TICKET", authentication(10L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldRejectAnonymousAndOperatorPrincipals() {
        assertThatThrownBy(() -> service.currentAccount(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.currentAccount(
                new UsernamePasswordAuthenticationToken("operator", null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    private UsernamePasswordAuthenticationToken authentication(Long accountId) {
        return new UsernamePasswordAuthenticationToken(
                new PassengerPrincipal(accountId, "passenger-a", 30L, "installation-a"),
                null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_PASSENGER")));
    }
}
