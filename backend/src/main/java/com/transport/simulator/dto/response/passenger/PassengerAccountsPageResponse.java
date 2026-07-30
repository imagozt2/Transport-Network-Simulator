package com.transport.simulator.dto.response.passenger;

import java.util.List;

public record PassengerAccountsPageResponse(
        PassengerAccountSummaryResponse summary,
        List<PassengerAccountResponse> users,
        int page,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {
}
