package com.transport.simulator.controller;

import com.transport.simulator.dto.request.passenger.PassengerAccountStatusUpdateRequest;
import com.transport.simulator.dto.request.passenger.PassengerAccountCreateRequest;
import com.transport.simulator.dto.response.passenger.PassengerAccountResponse;
import com.transport.simulator.dto.response.passenger.PassengerAccountsPageResponse;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.service.PassengerAccountManagementService;
import com.transport.simulator.service.PassengerAccountQueryService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/admin/passenger-users")
public class PassengerAccountAdminController {

    private final PassengerAccountQueryService passengerAccountQueryService;
    private final PassengerAccountManagementService passengerAccountManagementService;

    public PassengerAccountAdminController(
            PassengerAccountQueryService passengerAccountQueryService,
            PassengerAccountManagementService passengerAccountManagementService
    ) {
        this.passengerAccountQueryService = passengerAccountQueryService;
        this.passengerAccountManagementService = passengerAccountManagementService;
    }

    @GetMapping
    public PassengerAccountsPageResponse getAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PassengerAccountStatus status,
            @RequestParam(required = false) Boolean emailVerified,
            @RequestParam(defaultValue = "registeredAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction
    ) {
        return passengerAccountQueryService.getAccounts(
                page,
                size,
                search,
                status,
                emailVerified,
                sortBy,
                direction
        );
    }

    @GetMapping("/{publicId}")
    public PassengerAccountResponse getAccount(@PathVariable String publicId) {
        return passengerAccountQueryService.getAccount(publicId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PassengerAccountResponse createAccount(
            @Valid @RequestBody PassengerAccountCreateRequest request,
            Authentication authentication
    ) {
        return passengerAccountManagementService.createAccount(request, authentication);
    }

    @PatchMapping("/{publicId}/status")
    public PassengerAccountResponse updateStatus(
            @PathVariable String publicId,
            @Valid @RequestBody PassengerAccountStatusUpdateRequest request,
            Authentication authentication
    ) {
        return passengerAccountManagementService.updateStatus(
                publicId,
                request,
                authentication
        );
    }
}
