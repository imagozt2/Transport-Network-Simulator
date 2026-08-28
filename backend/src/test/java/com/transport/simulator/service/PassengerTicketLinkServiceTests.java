package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.request.passengerticket.PassengerTicketLinkRequest;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketDetailResponse;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketOperation;
import com.transport.simulator.entity.TicketQrCredential;
import com.transport.simulator.entity.TicketSupport;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.enums.TicketStatus;
import com.transport.simulator.enums.TicketSupportStatus;
import com.transport.simulator.enums.TicketSupportType;
import com.transport.simulator.repository.TicketOperationRepository;
import com.transport.simulator.repository.TicketJourneyRepository;
import com.transport.simulator.ticketing.qr.TicketQrVerificationException;
import com.transport.simulator.ticketing.qr.TicketQrVerificationFailure;
import com.transport.simulator.ticketing.qr.TicketQrVerifier;
import com.transport.simulator.ticketing.qr.VerifiedTicketQr;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PassengerTicketLinkServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-11T10:00:00Z"), ZoneOffset.UTC
    );

    @Mock private PassengerResourceAccessService accessService;
    @Mock private PassengerTicketQueryService queryService;
    @Mock private TicketQrVerifier qrVerifier;
    @Mock private TicketOperationRepository operationRepository;
    @Mock private TicketJourneyRepository journeyRepository;
    @Mock private TicketOperationRegistrationService registrationService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private Authentication authentication;
    @Mock private PassengerAccount passenger;
    @Mock private Ticket ticket;
    @Mock private TicketSupport support;
    @Mock private TicketQrCredential credential;
    @Mock private PassengerTicketDetailResponse response;

    private PassengerTicketLinkService service;

    @BeforeEach
    void setUp() {
        service = new PassengerTicketLinkService(
                accessService, queryService, qrVerifier, operationRepository,
                journeyRepository, registrationService, passwordEncoder, CLOCK
        );
    }

    @Test
    void shouldLinkAValidPhysicalTicketAndTreatAnIdenticalRetryAsIdempotent() {
        prepareValidLink();
        PassengerTicketLinkRequest request = new PassengerTicketLinkRequest(
                "RMM:TICKET:1:signed", "ABCD-1234"
        );

        assertThat(service.link("link-request-00000001", request, authentication)).isSameAs(response);

        ArgumentCaptor<String> reference = ArgumentCaptor.forClass(String.class);
        verify(registrationService).recordSupportLink(
                any(), any(), any(), reference.capture()
        );
        TicketOperation previous = mock(TicketOperation.class);
        when(previous.getExternalReference()).thenReturn(reference.getValue());
        when(previous.getPassengerAccount()).thenReturn(passenger);
        when(previous.getTicket()).thenReturn(ticket);
        when(operationRepository.findFirstByTypeAndSourceAndExternalReferenceStartingWith(
                any(), any(), anyString()
        )).thenReturn(Optional.of(previous));

        assertThat(service.link("link-request-00000001", request, authentication)).isSameAs(response);

        verify(qrVerifier, times(1)).verify("RMM:TICKET:1:signed");
        verify(support, times(1)).linkToPassenger(passenger, LocalDateTime.now(CLOCK));
    }

    @Test
    void shouldHideQrVerificationDetailsBehindAnInvalidLinkProof() {
        when(accessService.currentAccount(authentication)).thenReturn(passenger);
        when(passenger.getStatus()).thenReturn(PassengerAccountStatus.ACTIVE);
        when(operationRepository.findFirstByTypeAndSourceAndExternalReferenceStartingWith(
                any(), any(), anyString()
        )).thenReturn(Optional.empty());
        when(qrVerifier.verify(anyString())).thenThrow(
                new TicketQrVerificationException(TicketQrVerificationFailure.INVALID_SIGNATURE)
        );

        assertThatThrownBy(() -> service.link(
                "link-request-00000002",
                new PassengerTicketLinkRequest("RMM:TICKET:1:tampered", "ABCD"),
                authentication
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(exception.getReason()).isEqualTo("INVALID_LINK_CODE");
        });
    }

    @Test
    void shouldRejectATicketOwnedByAnotherPassenger() {
        prepareValidLink();
        PassengerAccount anotherPassenger = mock(PassengerAccount.class);
        when(anotherPassenger.getId()).thenReturn(2L);
        when(ticket.getPassengerAccount()).thenReturn(anotherPassenger);

        assertThatThrownBy(() -> service.link(
                "link-request-00000003",
                new PassengerTicketLinkRequest("RMM:TICKET:1:signed", "ABCD"),
                authentication
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getReason()).isEqualTo("TICKET_ALREADY_LINKED");
        });
    }

    private void prepareValidLink() {
        when(accessService.currentAccount(authentication)).thenReturn(passenger);
        when(passenger.getId()).thenReturn(1L);
        when(passenger.getStatus()).thenReturn(PassengerAccountStatus.ACTIVE);
        when(operationRepository.findFirstByTypeAndSourceAndExternalReferenceStartingWith(
                any(), any(), anyString()
        )).thenReturn(Optional.empty());
        when(qrVerifier.verify("RMM:TICKET:1:signed")).thenReturn(
                new VerifiedTicketQr(null, credential, "key", "fingerprint")
        );
        when(credential.getSupport()).thenReturn(support);
        when(credential.getTicket()).thenReturn(ticket);
        when(ticket.getId()).thenReturn(10L);
        when(ticket.getCode()).thenReturn("RMM-TKT-001");
        when(ticket.getStatus()).thenReturn(TicketStatus.ACTIVE);
        when(ticket.getBalanceAmount()).thenReturn(BigDecimal.ZERO);
        when(support.getType()).thenReturn(TicketSupportType.PHYSICAL);
        when(support.getStatus()).thenReturn(TicketSupportStatus.ACTIVE);
        when(support.getTicket()).thenReturn(ticket);
        when(support.getLinkingCodeHash()).thenReturn("encoded-link-code");
        when(support.getLinkingCodeExpiresAt()).thenReturn(LocalDateTime.now(CLOCK).plusMinutes(10));
        when(passwordEncoder.matches("ABCD1234", "encoded-link-code")).thenReturn(true);
        when(passwordEncoder.matches("ABCD", "encoded-link-code")).thenReturn(true);
        when(queryService.ticket(anyString(), any())).thenReturn(response);
    }
}
