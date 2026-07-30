package com.transport.simulator.service;

import com.transport.simulator.dto.response.passenger.PassengerAccountResponse;
import com.transport.simulator.dto.response.passenger.PassengerAccountsPageResponse;
import com.transport.simulator.dto.response.passenger.PassengerAccountSummaryResponse;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.repository.PassengerAccountRepository;
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
public class PassengerAccountQueryService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "registeredAt", "createdAt",
            "name", "lastName",
            "email", "email",
            "status", "status",
            "lastLoginAt", "lastLoginAt"
    );

    private final PassengerAccountRepository passengerAccountRepository;

    public PassengerAccountQueryService(PassengerAccountRepository passengerAccountRepository) {
        this.passengerAccountRepository = passengerAccountRepository;
    }

    public PassengerAccountsPageResponse getAccounts(
            int page,
            int size,
            String search,
            PassengerAccountStatus status,
            Boolean emailVerified,
            String sortBy,
            String direction
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Sort sort = buildSort(sortBy, direction);
        Page<PassengerAccount> result = passengerAccountRepository.findAdministrativePage(
                normalizeSearch(search),
                status,
                emailVerified,
                PageRequest.of(safePage, safeSize, sort)
        );

        return new PassengerAccountsPageResponse(
                getSummary(),
                result.getContent().stream().map(PassengerAccountResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                result.isEmpty()
        );
    }

    public PassengerAccountResponse getAccount(String publicId) {
        String normalizedPublicId = publicId == null ? "" : publicId.trim();
        return passengerAccountRepository.findByPublicId(normalizedPublicId)
                .map(PassengerAccountResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Passenger account not found"
                ));
    }

    public PassengerAccountSummaryResponse getSummary() {
        return new PassengerAccountSummaryResponse(
                passengerAccountRepository.count(),
                passengerAccountRepository.countByStatus(PassengerAccountStatus.ACTIVE),
                passengerAccountRepository.countByStatus(PassengerAccountStatus.BLOCKED),
                passengerAccountRepository.countByStatus(PassengerAccountStatus.DISABLED),
                passengerAccountRepository.countByEmailVerifiedAtIsNull()
        );
    }

    private Sort buildSort(String sortBy, String direction) {
        String requestedSort = sortBy == null || sortBy.isBlank()
                ? "registeredAt"
                : sortBy.trim();
        String entityField = SORT_FIELDS.get(requestedSort);
        if (entityField == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field");
        }

        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(
                    direction == null || direction.isBlank() ? "DESC" : direction.trim()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sort direction must be ASC or DESC"
            );
        }

        Sort sort = Sort.by(sortDirection, entityField);
        if ("lastName".equals(entityField)) {
            sort = sort.and(Sort.by(sortDirection, "firstName"));
        }
        return sort.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
