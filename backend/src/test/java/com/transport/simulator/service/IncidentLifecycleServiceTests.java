package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.request.incident.IncidentCommentCreateRequest;
import com.transport.simulator.dto.request.incident.IncidentCreateRequest;
import com.transport.simulator.dto.request.incident.IncidentStatusUpdateRequest;
import com.transport.simulator.dto.request.incident.IncidentUpdateRequest;
import com.transport.simulator.entity.Incident;
import com.transport.simulator.entity.IncidentComment;
import com.transport.simulator.entity.IncidentStatusChange;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.enums.IncidentCategory;
import com.transport.simulator.enums.IncidentPriority;
import com.transport.simulator.enums.IncidentStatus;
import com.transport.simulator.enums.OperatorRole;
import com.transport.simulator.repository.DepotRepository;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.IncidentCommentRepository;
import com.transport.simulator.repository.IncidentRepository;
import com.transport.simulator.repository.IncidentStatusChangeRepository;
import com.transport.simulator.repository.OperatorAccountRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.repository.TransportLineRepository;
import com.transport.simulator.security.OperatorPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
class IncidentLifecycleServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T09:00:00Z"),
            ZoneId.of("Europe/Madrid")
    );

    @Mock private IncidentRepository incidentRepository;
    @Mock private IncidentStatusChangeRepository statusChangeRepository;
    @Mock private IncidentCommentRepository commentRepository;
    @Mock private OperatorAccountRepository operatorRepository;
    @Mock private TransportLineRepository lineRepository;
    @Mock private StationRepository stationRepository;
    @Mock private TrainRepository trainRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private DepotRepository depotRepository;

    private IncidentManagementService service;
    private OperatorAccount operator;

    @BeforeEach
    void setUp() {
        operator = new OperatorAccount(
                "operator", "operator@rmm.local", "stored-password-hash",
                "Ana", "Operadora", OperatorRole.OPERATOR
        );
        when(operatorRepository.findById(7L)).thenReturn(Optional.of(operator));
        when(incidentRepository.save(any(Incident.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(statusChangeRepository.save(any(IncidentStatusChange.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new IncidentManagementService(
                incidentRepository, statusChangeRepository, commentRepository,
                operatorRepository, lineRepository, stationRepository, trainRepository,
                deviceRepository, depotRepository, CLOCK
        );
    }

    @Test
    void shouldCoverCreationAssignmentCommentsResolutionAndClosure() {
        ArgumentCaptor<Incident> incidentCaptor = ArgumentCaptor.forClass(Incident.class);
        ArgumentCaptor<IncidentStatusChange> changes =
                ArgumentCaptor.forClass(IncidentStatusChange.class);

        var created = service.create(new IncidentCreateRequest(
                "Fallo de validación", "Una validadora no procesa títulos físicos",
                IncidentCategory.DEVICE, IncidentPriority.HIGH,
                7L, null, null, null, null, null
        ), authentication());
        verify(incidentRepository).save(incidentCaptor.capture());
        Incident incident = incidentCaptor.getValue();
        when(incidentRepository.findByCode(created.code())).thenReturn(Optional.of(incident));
        when(commentRepository.save(any(IncidentComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(created.code()).startsWith("INC-");
        assertThat(created.status()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getAssignedTo()).isSameAs(operator);

        var comment = service.addComment(
                created.code(), new IncidentCommentCreateRequest("Diagnóstico iniciado"),
                authentication()
        );
        assertThat(comment.text()).isEqualTo("Diagnóstico iniciado");
        assertThat(comment.author().username()).isEqualTo("operator");

        service.changeStatus(created.code(), new IncidentStatusUpdateRequest(
                IncidentStatus.IN_PROGRESS, "Revisión remota", null
        ), authentication());
        service.changeStatus(created.code(), new IncidentStatusUpdateRequest(
                IncidentStatus.RESOLVED, "Pruebas correctas", "Se reinició el lector QR"
        ), authentication());
        var closed = service.changeStatus(created.code(), new IncidentStatusUpdateRequest(
                IncidentStatus.CLOSED, "Cierre validado", null
        ), authentication());

        verify(statusChangeRepository, org.mockito.Mockito.times(4)).save(changes.capture());
        assertThat(changes.getAllValues()).extracting(IncidentStatusChange::getNewStatus)
                .containsExactly(
                        IncidentStatus.OPEN,
                        IncidentStatus.IN_PROGRESS,
                        IncidentStatus.RESOLVED,
                        IncidentStatus.CLOSED
                );
        assertThat(closed.status()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(closed.resolutionSummary()).isEqualTo("Se reinició el lector QR");
        assertThat(closed.resolvedAt()).isNotNull();
        assertThat(closed.closedAt()).isNotNull();
    }

    @Test
    void shouldRejectResolutionWithoutSummaryAndChangesAfterClosure() {
        Incident incident = incident();
        when(incidentRepository.findByCode("INC-TEST")).thenReturn(Optional.of(incident));
        service.changeStatus("INC-TEST", new IncidentStatusUpdateRequest(
                IncidentStatus.IN_PROGRESS, null, null
        ), authentication());

        assertThatThrownBy(() -> service.changeStatus(
                "INC-TEST",
                new IncidentStatusUpdateRequest(IncidentStatus.RESOLVED, null, " "),
                authentication()
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        service.changeStatus("INC-TEST", new IncidentStatusUpdateRequest(
                IncidentStatus.RESOLVED, null, "Incidencia corregida"
        ), authentication());
        service.changeStatus("INC-TEST", new IncidentStatusUpdateRequest(
                IncidentStatus.CLOSED, null, null
        ), authentication());

        assertThatThrownBy(() -> service.update(
                "INC-TEST",
                new IncidentUpdateRequest(
                        "Título modificado", "Descripción modificada",
                        IncidentCategory.OTHER, IncidentPriority.LOW,
                        null, null, null, null, null, null
                ),
                authentication()
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.addComment(
                "INC-TEST", new IncidentCommentCreateRequest("Comentario tardío"),
                authentication()
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(commentRepository, never()).save(any());
    }

    private Incident incident() {
        return new Incident(
                "INC-TEST", "Incidencia de prueba", "Descripción de prueba",
                IncidentCategory.SERVICE, IncidentPriority.MEDIUM, operator,
                java.time.LocalDateTime.now(CLOCK)
        );
    }

    private Authentication authentication() {
        OperatorPrincipal principal = new OperatorPrincipal(
                7L, "operator", "operator@rmm.local", "Ana", "Operadora",
                OperatorRole.OPERATOR
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, java.util.List.of()
        );
    }
}
