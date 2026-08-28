package com.transport.simulator.dto.request.operator;

import com.transport.simulator.enums.OperatorTheme;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OperatorDisplayPreferencesRequest(
        @NotBlank @Size(max = 64) String timeZone,
        @NotNull OperatorTheme theme
) {
}
