package com.transport.simulator.controller;

import com.transport.simulator.dto.request.transporttitle.CompensatoryTicketIssuanceRequest;
import com.transport.simulator.dto.response.transporttitle.CompensatoryTicketIssuanceResponse;
import com.transport.simulator.dto.response.transporttitle.TransportTitleResponse;
import com.transport.simulator.dto.response.transporttitle.TransportTitlesResponse;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.service.TransportTitleQueryService;
import com.transport.simulator.service.CompensatoryTicketIssuanceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transport-titles")
public class TransportTitleController {

    private final TransportTitleQueryService queryService;
    private final CompensatoryTicketIssuanceService issuanceService;

    @Autowired
    public TransportTitleController(
            TransportTitleQueryService queryService,
            CompensatoryTicketIssuanceService issuanceService
    ) {
        this.queryService = queryService;
        this.issuanceService = issuanceService;
    }

    TransportTitleController(TransportTitleQueryService queryService) {
        this(queryService, null);
    }

    @GetMapping
    public TransportTitlesResponse getTitles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TicketProductType type,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean rechargeable
    ) {
        return queryService.getTitles(search, type, active, rechargeable);
    }

    @GetMapping("/{titleId}")
    public TransportTitleResponse getTitle(@PathVariable long titleId) {
        return queryService.getTitle(titleId);
    }

    @GetMapping("/code/{code}")
    public TransportTitleResponse getTitleByCode(@PathVariable String code) {
        return queryService.getTitle(code);
    }

    @PostMapping("/{titleId}/compensatory-issuances")
    @ResponseStatus(HttpStatus.CREATED)
    public CompensatoryTicketIssuanceResponse issueCompensatoryTicket(
            @PathVariable long titleId,
            @Valid @RequestBody CompensatoryTicketIssuanceRequest request,
            Authentication authentication
    ) {
        return issuanceService.issue(titleId, request, authentication);
    }
}
