package com.transport.simulator.service;

import com.transport.simulator.dto.request.incident.IncidentCommentCreateRequest;
import com.transport.simulator.dto.request.incident.IncidentCreateRequest;
import com.transport.simulator.dto.request.incident.IncidentStatusUpdateRequest;
import com.transport.simulator.dto.request.incident.IncidentUpdateRequest;
import com.transport.simulator.dto.response.incident.IncidentCommentResponse;
import com.transport.simulator.dto.response.incident.IncidentResponse;
import com.transport.simulator.entity.Depot;
import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.Incident;
import com.transport.simulator.entity.IncidentComment;
import com.transport.simulator.entity.IncidentStatusChange;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Train;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.IncidentStatus;
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
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IncidentManagementService {

    private final IncidentRepository incidentRepository;
    private final IncidentStatusChangeRepository statusChangeRepository;
    private final IncidentCommentRepository commentRepository;
    private final OperatorAccountRepository operatorRepository;
    private final TransportLineRepository lineRepository;
    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final DeviceRepository deviceRepository;
    private final DepotRepository depotRepository;
    private final Clock clock;

    public IncidentManagementService(
            IncidentRepository incidentRepository,
            IncidentStatusChangeRepository statusChangeRepository,
            IncidentCommentRepository commentRepository,
            OperatorAccountRepository operatorRepository,
            TransportLineRepository lineRepository,
            StationRepository stationRepository,
            TrainRepository trainRepository,
            DeviceRepository deviceRepository,
            DepotRepository depotRepository,
            Clock clock
    ) {
        this.incidentRepository = incidentRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.commentRepository = commentRepository;
        this.operatorRepository = operatorRepository;
        this.lineRepository = lineRepository;
        this.stationRepository = stationRepository;
        this.trainRepository = trainRepository;
        this.deviceRepository = deviceRepository;
        this.depotRepository = depotRepository;
        this.clock = clock;
    }

    @Transactional
    public IncidentResponse create(IncidentCreateRequest request, Authentication authentication) {
        OperatorAccount creator = requireOperator(authentication);
        LocalDateTime now = LocalDateTime.now(clock);
        Incident incident = new Incident(
                nextCode(), request.title(), request.description(), request.category(),
                request.priority(), creator, now
        );
        applyAssignmentAndResources(incident, request.assignedOperatorId(), request.affectedLineId(),
                request.affectedStationId(), request.affectedTrainId(), request.affectedDeviceId(),
                request.affectedDepotId(), now);
        incidentRepository.save(incident);
        statusChangeRepository.save(new IncidentStatusChange(
                incident, creator, null, IncidentStatus.OPEN, "Incident created", now
        ));
        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentResponse update(
            String code,
            IncidentUpdateRequest request,
            Authentication authentication
    ) {
        requireOperator(authentication);
        Incident incident = requireIncident(code);
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            incident.updateDetails(request.title(), request.description(), request.category(), request.priority());
            applyAssignmentAndResources(incident, request.assignedOperatorId(), request.affectedLineId(),
                    request.affectedStationId(), request.affectedTrainId(), request.affectedDeviceId(),
                    request.affectedDepotId(), now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        return IncidentResponse.from(incidentRepository.save(incident));
    }

    @Transactional
    public IncidentResponse changeStatus(
            String code,
            IncidentStatusUpdateRequest request,
            Authentication authentication
    ) {
        OperatorAccount operator = requireOperator(authentication);
        Incident incident = requireIncident(code);
        LocalDateTime now = LocalDateTime.now(clock);
        IncidentStatus previous;
        try {
            previous = incident.changeStatus(request.status(), request.resolutionSummary(), now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        incidentRepository.save(incident);
        statusChangeRepository.save(new IncidentStatusChange(
                incident, operator, previous, request.status(), request.note(), now
        ));
        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentCommentResponse addComment(
            String code,
            IncidentCommentCreateRequest request,
            Authentication authentication
    ) {
        OperatorAccount operator = requireOperator(authentication);
        Incident incident = requireIncident(code);
        if (incident.getStatus() == IncidentStatus.CLOSED
                || incident.getStatus() == IncidentStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Closed or cancelled incidents cannot receive comments"
            );
        }
        return IncidentCommentResponse.from(commentRepository.save(new IncidentComment(
                incident, operator, request.text(), LocalDateTime.now(clock)
        )));
    }

    private void applyAssignmentAndResources(
            Incident incident,
            Long operatorId,
            Long lineId,
            Long stationId,
            Long trainId,
            Long deviceId,
            Long depotId,
            LocalDateTime now
    ) {
        incident.assignTo(optional(operatorRepository, operatorId, "Assigned operator"), now);
        TransportLine line = optional(lineRepository, lineId, "Affected line");
        Station station = optional(stationRepository, stationId, "Affected station");
        Train train = optional(trainRepository, trainId, "Affected train");
        Device device = optional(deviceRepository, deviceId, "Affected device");
        Depot depot = optional(depotRepository, depotId, "Affected depot");
        incident.setAffectedResources(line, station, train, device, depot);
    }

    private <T> T optional(JpaRepository<T, Long> repository, Long id, String label) {
        if (id == null) {
            return null;
        }
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                label + " does not exist"
        ));
    }

    private OperatorAccount requireOperator(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OperatorPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return operatorRepository.findById(principal.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated operator no longer exists"));
    }

    private Incident requireIncident(String code) {
        return incidentRepository.findByCode(normalizeCode(code)).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }

    private String nextCode() {
        return "INC-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
