package com.transport.simulator.dto.request.passenger;

import com.transport.simulator.enums.PassengerAccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PassengerAccountStatusUpdateRequest(
        @NotNull PassengerAccountStatus status,
        @Size(max = 500) String reason
) {
}
