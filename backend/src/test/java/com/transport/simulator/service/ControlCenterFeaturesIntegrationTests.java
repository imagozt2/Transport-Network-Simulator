package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.request.incident.IncidentCommentCreateRequest;
import com.transport.simulator.dto.request.incident.IncidentCreateRequest;
import com.transport.simulator.dto.request.incident.IncidentStatusUpdateRequest;
import com.transport.simulator.dto.request.passenger.PassengerAccountCreateRequest;
import com.transport.simulator.dto.request.transporttitle.CompensatoryTicketIssuanceRequest;
import com.transport.simulator.entity.CompensatoryTicketIssuance;
import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.entity.Incident;
import com.transport.simulator.entity.IncidentComment;
import com.transport.simulator.entity.IncidentStatusChange;
import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.CompensatoryIssuanceStatus;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.IncidentCategory;
import com.transport.simulator.enums.IncidentPriority;
import com.transport.simulator.enums.IncidentStatus;
import com.transport.simulator.enums.OperatorRole;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.repository.CompensatoryTicketIssuanceRepository;
import com.transport.simulator.repository.DepotRepository;
import com.transport.simulator.repository.DeviceEventLogRepository;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.IncidentCommentRepository;
import com.transport.simulator.repository.IncidentRepository;
import com.transport.simulator.repository.IncidentStatusChangeRepository;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.OperatorAccountRepository;
import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.PassengerAccountStatusChangeRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketProductRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.repository.TransportLineRepository;
import com.transport.simulator.security.OperatorPrincipal;
import com.transport.simulator.service.model.NetworkJourney;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class ControlCenterFeaturesIntegrationTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T09:00:00Z"),
            ZoneId.of("Europe/Madrid")
    );

    @Test
    void shouldCalculateAJourneyIssueTheTicketAndRegisterItsAuditEvents() {
        Station origin = station(1L, "ST001", "Aeropuerto");
        Station intermediate = station(2L, "ST002", "HUB Industrial Norte");
        Station destination = station(3L, "ST003", "Plaza de la Merced");
        TransportLine line = line(1L, "L2", "Línea 2");
        StationRepository stationRepository = mock(StationRepository.class);
        LineStationRepository lineStationRepository = mock(LineStationRepository.class);
        when(stationRepository.findByCodeAndActiveTrue("ST001")).thenReturn(Optional.of(origin));
        when(stationRepository.findByCodeAndActiveTrue("ST003")).thenReturn(Optional.of(destination));
        when(lineStationRepository.findAllByActiveTrueOrderByLineCodeAscStationOrderAsc())
                .thenReturn(List.of(
                        stop(line, origin, 1, 90),
                        stop(line, intermediate, 2, 120),
                        stop(line, destination, 3, null)
                ));
        NetworkJourneyPlanningService journeyService =
                new NetworkJourneyPlanningService(stationRepository, lineStationRepository);

        TicketProduct product = mock(TicketProduct.class);
        when(product.isActive()).thenReturn(true);
        when(product.getCode()).thenReturn("SINGLE_TRIP");
        when(product.getProductType()).thenReturn(TicketProductType.SINGLE_TRIP);
        when(product.getBasePrice()).thenReturn(new BigDecimal("0.50"));
        when(product.getPricePerStation()).thenReturn(new BigDecimal("0.05"));
        Device device = mock(Device.class);
        when(device.getCode()).thenReturn("TM-ST001-01");
        when(device.getName()).thenReturn("Máquina de Aeropuerto 1");
        when(device.getType()).thenReturn(DeviceType.TICKET_MACHINE);
        when(device.getStatus()).thenReturn(DeviceStatus.ONLINE);
        when(device.getStation()).thenReturn(origin);
        OperatorAccount operator = operator(7L, "admin", OperatorRole.ADMINISTRATOR);

        TicketProductRepository productRepository = mock(TicketProductRepository.class);
        DeviceRepository deviceRepository = mock(DeviceRepository.class);
        OperatorAccountRepository operatorRepository = mock(OperatorAccountRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        CompensatoryTicketIssuanceRepository issuanceRepository =
                mock(CompensatoryTicketIssuanceRepository.class);
        DeviceEventLogRepository logRepository = mock(DeviceEventLogRepository.class);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(deviceRepository.findByCodeAndActiveTrue("TM-ST001-01")).thenReturn(Optional.of(device));
        when(operatorRepository.findById(7L)).thenReturn(Optional.of(operator));
        when(issuanceRepository.save(any(CompensatoryTicketIssuance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(logRepository.save(any(DeviceEventLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketIssuanceEventRegistrationService eventService =
                new TicketIssuanceEventRegistrationService(logRepository, new ObjectMapper());
        CompensatoryTicketIssuanceService issuanceService = new CompensatoryTicketIssuanceService(
                productRepository, deviceRepository, stationRepository, operatorRepository,
                ticketRepository, issuanceRepository, eventService, journeyService, CLOCK
        );

        NetworkJourney plannedJourney = journeyService.calculate("ST001", "ST003");
        CompensatoryTicketIssuanceRequest request = new CompensatoryTicketIssuanceRequest(
                "tm-st001-01", "Compensación por fallo de impresión",
                "st001", "st003", null, null, null
        );
        var response = issuanceService.issue(
                1L, request,
                authentication(7L, "admin", OperatorRole.ADMINISTRATOR)
        );

        ArgumentCaptor<CompensatoryTicketIssuance> issuance =
                ArgumentCaptor.forClass(CompensatoryTicketIssuance.class);
        ArgumentCaptor<DeviceEventLog> logs = ArgumentCaptor.forClass(DeviceEventLog.class);
        verify(issuanceRepository).save(issuance.capture());
        verify(logRepository, times(2)).save(logs.capture());
        assertThat(plannedJourney.stationCount()).isEqualTo(3);
        assertThat(ReflectionTestUtils.getField(issuance.getValue(), "stationCount")).isEqualTo(3);
        assertThat(response.status()).isEqualTo(CompensatoryIssuanceStatus.COMPLETED);
        assertThat(response.chargedAmount()).isZero();
        assertThat(response.stationCode()).isEqualTo("ST001");
        assertThat(logs.getAllValues()).extracting(DeviceEventLog::getEventType)
                .containsExactly(
                        DeviceEventType.COMPENSATORY_TICKET_ISSUANCE_REQUESTED,
                        DeviceEventType.COMPENSATORY_TICKET_ISSUED
                );
        assertThat(logs.getAllValues()).allSatisfy(log -> {
            assertThat(log.getOperator()).isSameAs(operator);
            assertThat(log.getDevice()).isSameAs(device);
            assertThat(log.getStation()).isSameAs(origin);
            assertThat(log.getCompensatoryIssuance()).isSameAs(issuance.getValue());
            assertThat(log.getExternalReference()).startsWith(issuance.getValue().getCode());
        });
        assertThat(logs.getAllValues().getLast().getPayloadJson())
                .contains("SINGLE_TRIP", response.ticketCode(), "TM-ST001-01", "admin");

        assertThatThrownBy(() -> issuanceService.issue(1L, request, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(issuanceRepository).save(any(CompensatoryTicketIssuance.class));
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void shouldCreateAndQueryAPassengerThenResolveAnIncidentWithTheSameAdministrator() {
        Authentication authentication = authentication(7L, "admin", OperatorRole.ADMINISTRATOR);
        OperatorAccount administrator = operator(7L, "admin", OperatorRole.ADMINISTRATOR);
        OperatorAccountRepository operatorRepository = mock(OperatorAccountRepository.class);
        when(operatorRepository.findById(7L)).thenReturn(Optional.of(administrator));

        PassengerAccountRepository passengerRepository = mock(PassengerAccountRepository.class);
        PassengerAccountStatusChangeRepository passengerChanges =
                mock(PassengerAccountStatusChangeRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AtomicReference<PassengerAccount> storedPassenger = new AtomicReference<>();
        when(passwordEncoder.encode("SecurePassword123")).thenReturn("encoded-password");
        when(passengerRepository.existsByEmailIgnoreCase("ana@example.local")).thenReturn(false);
        when(passengerRepository.save(any(PassengerAccount.class))).thenAnswer(invocation -> {
            PassengerAccount passenger = invocation.getArgument(0);
            ReflectionTestUtils.setField(passenger, "id", 21L);
            storedPassenger.set(passenger);
            return passenger;
        });
        when(passengerRepository.findByPublicId(any())).thenAnswer(invocation ->
                Optional.ofNullable(storedPassenger.get())
                        .filter(passenger -> passenger.getPublicId().equals(invocation.getArgument(0)))
        );
        PassengerAccountManagementService passengerManagement =
                new PassengerAccountManagementService(
                        passengerRepository, passengerChanges, operatorRepository, passwordEncoder
                );
        PassengerAccountQueryService passengerQuery =
                new PassengerAccountQueryService(passengerRepository);

        PassengerAccountCreateRequest passengerRequest = new PassengerAccountCreateRequest(
                " ANA@Example.Local ", "SecurePassword123", "Ana", "García"
        );
        assertThatThrownBy(() -> passengerManagement.createAccount(
                passengerRequest,
                authentication(8L, "operator", OperatorRole.OPERATOR)
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(passengerRepository, never()).save(any(PassengerAccount.class));

        var createdPassenger = passengerManagement.createAccount(
                passengerRequest,
                authentication
        );
        var queriedPassenger = passengerQuery.getAccount(createdPassenger.publicId());

        assertThat(queriedPassenger.email()).isEqualTo("ana@example.local");
        assertThat(queriedPassenger.firstName()).isEqualTo("Ana");

        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        IncidentStatusChangeRepository incidentChanges = mock(IncidentStatusChangeRepository.class);
        IncidentCommentRepository comments = mock(IncidentCommentRepository.class);
        AtomicReference<Incident> storedIncident = new AtomicReference<>();
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            storedIncident.set(incident);
            return incident;
        });
        when(incidentRepository.findByCode(any())).thenAnswer(invocation ->
                Optional.ofNullable(storedIncident.get())
                        .filter(incident -> incident.getCode().equals(invocation.getArgument(0)))
        );
        when(incidentChanges.save(any(IncidentStatusChange.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(comments.save(any(IncidentComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        IncidentManagementService incidentService = new IncidentManagementService(
                incidentRepository, incidentChanges, comments, operatorRepository,
                mock(TransportLineRepository.class), mock(StationRepository.class),
                mock(TrainRepository.class), mock(DeviceRepository.class),
                mock(DepotRepository.class), CLOCK
        );

        var createdIncident = incidentService.create(
                new IncidentCreateRequest(
                        "Billete no emitido", "El pasajero no recibió el título adquirido",
                        IncidentCategory.TICKETING, IncidentPriority.MEDIUM,
                        7L, null, null, null, null, null
                ),
                authentication
        );
        var comment = incidentService.addComment(
                createdIncident.code(),
                new IncidentCommentCreateRequest(
                        "Cuenta afectada: " + queriedPassenger.publicId()
                ),
                authentication
        );
        incidentService.changeStatus(
                createdIncident.code(),
                new IncidentStatusUpdateRequest(
                        IncidentStatus.IN_PROGRESS, "Compensación en preparación", null
                ),
                authentication
        );
        var resolved = incidentService.changeStatus(
                createdIncident.code(),
                new IncidentStatusUpdateRequest(
                        IncidentStatus.RESOLVED, "Billete entregado",
                        "Se emitió un billete compensatorio"
                ),
                authentication
        );

        ArgumentCaptor<IncidentStatusChange> auditChanges =
                ArgumentCaptor.forClass(IncidentStatusChange.class);
        verify(incidentChanges, times(3)).save(auditChanges.capture());
        assertThat(comment.text()).contains(queriedPassenger.publicId());
        assertThat(resolved.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.assignedTo().username()).isEqualTo("admin");
        assertThat(resolved.resolutionSummary()).isEqualTo("Se emitió un billete compensatorio");
        assertThat(auditChanges.getAllValues()).extracting(IncidentStatusChange::getNewStatus)
                .containsExactly(IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED);
        assertThat(auditChanges.getAllValues()).allSatisfy(change -> {
            assertThat(change.getChangedBy()).isSameAs(administrator);
            assertThat(change.getIncident().getCode()).isEqualTo(createdIncident.code());
        });
    }

    private Station station(Long id, String code, String name) {
        Station station = new Station(code, name);
        ReflectionTestUtils.setField(station, "id", id);
        return station;
    }

    private TransportLine line(Long id, String code, String name) {
        TransportLine line = new TransportLine(code, name, "#000000");
        ReflectionTestUtils.setField(line, "id", id);
        return line;
    }

    private LineStation stop(
            TransportLine line,
            Station station,
            int order,
            Integer travelSecondsToNext
    ) {
        LineStation stop = new LineStation(line, station, order);
        ReflectionTestUtils.setField(stop, "travelSecondsToNext", travelSecondsToNext);
        return stop;
    }

    private OperatorAccount operator(Long id, String username, OperatorRole role) {
        OperatorAccount operator = new OperatorAccount(
                username, username + "@rmm.local", "stored-password-hash",
                "Admin", "RMM", role
        );
        ReflectionTestUtils.setField(operator, "id", id);
        return operator;
    }

    private Authentication authentication(Long id, String username, OperatorRole role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new OperatorPrincipal(
                        id, username, username + "@rmm.local", "Admin", "RMM", role
                ),
                null,
                List.of()
        );
    }
}
