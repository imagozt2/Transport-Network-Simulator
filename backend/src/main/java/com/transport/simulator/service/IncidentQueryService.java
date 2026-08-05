package com.transport.simulator.service;

import com.transport.simulator.dto.response.incident.IncidentCommentResponse;
import com.transport.simulator.dto.response.incident.IncidentResponse;
import com.transport.simulator.dto.response.incident.IncidentStatusChangeResponse;
import com.transport.simulator.dto.response.incident.IncidentSummaryResponse;
import com.transport.simulator.dto.response.incident.IncidentsPageResponse;
import com.transport.simulator.entity.Incident;
import com.transport.simulator.enums.IncidentCategory;
import com.transport.simulator.enums.IncidentPriority;
import com.transport.simulator.enums.IncidentStatus;
import com.transport.simulator.repository.IncidentCommentRepository;
import com.transport.simulator.repository.IncidentRepository;
import com.transport.simulator.repository.IncidentStatusChangeRepository;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class IncidentQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "openedAt", "openedAt",
            "updatedAt", "updatedAt",
            "priority", "priority",
            "status", "status",
            "title", "title"
    );

    private final IncidentRepository incidentRepository;
    private final IncidentStatusChangeRepository statusChangeRepository;
    private final IncidentCommentRepository commentRepository;

    public IncidentQueryService(
            IncidentRepository incidentRepository,
            IncidentStatusChangeRepository statusChangeRepository,
            IncidentCommentRepository commentRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.commentRepository = commentRepository;
    }

    public IncidentsPageResponse getIncidents(
            int page,
            int size,
            String search,
            IncidentStatus status,
            IncidentPriority priority,
            IncidentCategory category,
            Long assignedOperatorId,
            String sortBy,
            String direction
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Page<Incident> result = incidentRepository.findManagementPage(
                normalizeSearch(search),
                status,
                priority,
                category,
                assignedOperatorId,
                PageRequest.of(safePage, safeSize, buildSort(sortBy, direction))
        );
        return new IncidentsPageResponse(
                getSummary(),
                result.getContent().stream().map(IncidentResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                result.isEmpty()
        );
    }

    public IncidentResponse getIncident(String code) {
        Incident incident = requireIncident(code);
        return IncidentResponse.from(
                incident,
                statusChangeRepository.findAllByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId())
                        .stream().map(IncidentStatusChangeResponse::from).toList(),
                commentRepository.findAllByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId())
                        .stream().map(IncidentCommentResponse::from).toList()
        );
    }

    public IncidentSummaryResponse getSummary() {
        return new IncidentSummaryResponse(
                incidentRepository.count(),
                incidentRepository.countByStatus(IncidentStatus.OPEN),
                incidentRepository.countByStatus(IncidentStatus.IN_PROGRESS),
                incidentRepository.countByStatus(IncidentStatus.RESOLVED),
                incidentRepository.countByStatus(IncidentStatus.CLOSED),
                incidentRepository.countByStatus(IncidentStatus.CANCELLED)
        );
    }

    private Incident requireIncident(String code) {
        return incidentRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Incident not found"
                ));
    }

    private Sort buildSort(String sortBy, String direction) {
        String requested = sortBy == null || sortBy.isBlank() ? "openedAt" : sortBy.trim();
        String field = SORT_FIELDS.get(requested);
        if (field == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field");
        }
        try {
            return Sort.by(Sort.Direction.fromString(
                    direction == null || direction.isBlank() ? "DESC" : direction.trim()
            ), field).and(Sort.by(Sort.Direction.ASC, "id"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sort direction must be ASC or DESC"
            );
        }
    }

    private String normalizeSearch(String search) {
        return search == null || search.isBlank()
                ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
