package com.transport.simulator.dto.request.passenger;

import com.transport.simulator.enums.PassengerDevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PassengerDeviceRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        String installationId,
        @NotBlank @Size(max = 100) String name,
        @NotNull PassengerDevicePlatform platform
) {
}
