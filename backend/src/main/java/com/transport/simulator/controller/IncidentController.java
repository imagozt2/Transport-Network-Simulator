package com.transport.simulator.controller;

import com.transport.simulator.dto.request.incident.IncidentCommentCreateRequest;
import com.transport.simulator.dto.request.incident.IncidentCreateRequest;
import com.transport.simulator.dto.request.incident.IncidentStatusUpdateRequest;
import com.transport.simulator.dto.request.incident.IncidentUpdateRequest;
import com.transport.simulator.dto.response.incident.IncidentCommentResponse;
import com.transport.simulator.dto.response.incident.IncidentResponse;
import com.transport.simulator.dto.response.incident.IncidentsPageResponse;
import com.transport.simulator.enums.IncidentCategory;
import com.transport.simulator.enums.IncidentPriority;
import com.transport.simulator.enums.IncidentStatus;
import com.transport.simulator.service.IncidentManagementService;
import com.transport.simulator.service.IncidentQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentQueryService queryService;
    private final IncidentManagementService managementService;

    public IncidentController(
            IncidentQueryService queryService,
            IncidentManagementService managementService
    ) {
        this.queryService = queryService;
        this.managementService = managementService;
    }

    @GetMapping
    public IncidentsPageResponse getIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentPriority priority,
            @RequestParam(required = false) IncidentCategory category,
            @RequestParam(required = false) Long assignedOperatorId,
            @RequestParam(defaultValue = "openedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction
    ) {
        return queryService.getIncidents(page, size, search, status, priority, category,
                assignedOperatorId, sortBy, direction);
    }

    @GetMapping("/{code}")
    public IncidentResponse getIncident(@PathVariable String code) {
        return queryService.getIncident(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse create(
            @Valid @RequestBody IncidentCreateRequest request,
            Authentication authentication
    ) {
        return managementService.create(request, authentication);
    }

    @PutMapping("/{code}")
    public IncidentResponse update(
            @PathVariable String code,
            @Valid @RequestBody IncidentUpdateRequest request,
            Authentication authentication
    ) {
        return managementService.update(code, request, authentication);
    }

    @PatchMapping("/{code}/status")
    public IncidentResponse changeStatus(
            @PathVariable String code,
            @Valid @RequestBody IncidentStatusUpdateRequest request,
            Authentication authentication
    ) {
        return managementService.changeStatus(code, request, authentication);
    }

    @PostMapping("/{code}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentCommentResponse addComment(
            @PathVariable String code,
            @Valid @RequestBody IncidentCommentCreateRequest request,
            Authentication authentication
    ) {
        return managementService.addComment(code, request, authentication);
    }
}
