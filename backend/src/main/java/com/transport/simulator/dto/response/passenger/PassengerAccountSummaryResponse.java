package com.transport.simulator.dto.response.passenger;

public record PassengerAccountSummaryResponse(
        long totalAccounts,
        long activeAccounts,
        long blockedAccounts,
        long disabledAccounts,
        long pendingVerificationAccounts
) {
}
