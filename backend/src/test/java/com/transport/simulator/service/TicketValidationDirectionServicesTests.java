package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.entity.TicketValidation;
import com.transport.simulator.enums.TicketJourneyStatus;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.enums.TicketQrValidationType;
import com.transport.simulator.enums.TicketStatus;
import com.transport.simulator.enums.TicketValidationStatus;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketJourneyRepository;
import com.transport.simulator.repository.TicketValidationRepository;
import com.transport.simulator.service.model.TicketValidationDecision;
import com.transport.simulator.service.model.TicketValidationRequest;
import com.transport.simulator.ticketing.qr.TicketQrUseGuard;
import com.transport.simulator.ticketing.qr.TicketQrVerifier;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class TicketValidationDirectionServicesTests {

    @Mock TicketJourneyRepository journeyRepository;
    @Mock SingleTripTicketService singleTripService;
    @Mock MultiTripTicketService multiTripService;
    @Mock TimePassTicketService timePassService;
    @Mock SmartBalanceTicketService smartBalanceService;

    @Test
    void entryConsumesTheMultiTripThroughItsDomainService() {
        Ticket ticket = ticket(TicketProductType.MULTI_TRIP, TicketStatus.ACTIVE, true);
        TicketJourney journey = mock(TicketJourney.class);
        when(journeyRepository.findFirstByTicketAndStatusOrderByOpenedAtDesc(
                ticket, TicketJourneyStatus.OPEN)).thenReturn(Optional.empty());
        when(multiTripService.enter("RMM-TKT-001", "ST038")).thenReturn(journey);

        TicketEntryValidationService service = entryService();

        assertThat(service.enter(ticket, "ST038")).isSameAs(journey);
        verify(multiTripService).enter("RMM-TKT-001", "ST038");
    }

    @Test
    void entryRejectsASecondOpenJourneyWithoutConsumingAnotherTrip() {
        Ticket ticket = ticket(TicketProductType.MULTI_TRIP, TicketStatus.ACTIVE, true);
        when(journeyRepository.findFirstByTicketAndStatusOrderByOpenedAtDesc(
                ticket, TicketJourneyStatus.OPEN)).thenReturn(Optional.of(mock(TicketJourney.class)));

        assertThatThrownBy(() -> entryService().enter(ticket, "ST038"))
                .isInstanceOf(TicketValidationRejectionException.class)
                .extracting(exception -> ((TicketValidationRejectionException) exception).getReasonCode())
                .isEqualTo("ENTRY_ALREADY_OPEN");
        verify(multiTripService, never()).enter("RMM-TKT-001", "ST038");
    }

    @Test
    void exitAllowsAnExhaustedMultiTripToCompleteItsOpenJourney() {
        Ticket ticket = ticket(TicketProductType.MULTI_TRIP, TicketStatus.EXHAUSTED, true);
        TicketJourney journey = mock(TicketJourney.class);
        when(journeyRepository.findFirstByTicketAndStatusOrderByOpenedAtDesc(
                ticket, TicketJourneyStatus.OPEN)).thenReturn(Optional.of(journey));
        when(multiTripService.exit("RMM-TKT-001", "ST040")).thenReturn(journey);

        assertThat(exitService().exit(ticket, "ST040")).isSameAs(journey);
        verify(multiTripService).exit("RMM-TKT-001", "ST040");
    }

    @Test
    void exitRejectsATicketWithoutAnEntry() {
        Ticket ticket = ticket(TicketProductType.SMART_BALANCE, TicketStatus.ACTIVE, true);
        when(journeyRepository.findFirstByTicketAndStatusOrderByOpenedAtDesc(
                ticket, TicketJourneyStatus.OPEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exitService().exit(ticket, "ST040"))
                .isInstanceOf(TicketValidationRejectionException.class)
                .extracting(exception -> ((TicketValidationRejectionException) exception).getReasonCode())
                .isEqualTo("ENTRY_REQUIRED");
        verify(smartBalanceService, never()).exit("RMM-TKT-001", "ST040");
    }

    @Test
    void anExistingReferenceReturnsItsDecisionWithoutVerifyingTheQrAgain() {
        TicketValidationRepository validations = mock(TicketValidationRepository.class);
        TicketQrVerifier verifier = mock(TicketQrVerifier.class);
        TicketValidation persisted = mock(TicketValidation.class);
        Station station = mock(Station.class);
        LocalDateTime decidedAt = LocalDateTime.of(2026, 8, 11, 12, 0);
        when(persisted.getExternalReference()).thenReturn("validation-reference");
        when(persisted.getStatus()).thenReturn(TicketValidationStatus.ACCEPTED);
        when(persisted.getStation()).thenReturn(station);
        when(station.getCode()).thenReturn("ST038");
        when(persisted.getValidatedAt()).thenReturn(decidedAt);
        when(validations.findByExternalReference("validation-reference"))
                .thenReturn(Optional.of(persisted));
        TicketValidationService service = new TicketValidationService(
                mock(DeviceRepository.class), mock(StationRepository.class), validations,
                verifier, mock(TicketQrUseGuard.class), entryService(), exitService(),
                mock(PlatformTransactionManager.class), Clock.systemUTC());

        TicketValidationDecision decision = service.validate(42L, new TicketValidationRequest(
                "validation-reference", TicketQrValidationType.ENTRY, "ST038", "qr-value"));

        assertThat(decision.decision()).isEqualTo(TicketValidationStatus.ACCEPTED);
        assertThat(decision.validationReference()).isEqualTo("validation-reference");
        verifyNoInteractions(verifier);
    }

    private TicketEntryValidationService entryService() {
        return new TicketEntryValidationService(journeyRepository, singleTripService,
                multiTripService, timePassService, smartBalanceService);
    }

    private TicketExitValidationService exitService() {
        return new TicketExitValidationService(journeyRepository, singleTripService,
                multiTripService, timePassService, smartBalanceService);
    }

    private Ticket ticket(TicketProductType type, TicketStatus status, boolean active) {
        Ticket ticket = mock(Ticket.class);
        lenient().when(ticket.getCode()).thenReturn("RMM-TKT-001");
        lenient().when(ticket.getProductType()).thenReturn(type);
        lenient().when(ticket.getStatus()).thenReturn(status);
        lenient().when(ticket.isActive()).thenReturn(active);
        return ticket;
    }
}
