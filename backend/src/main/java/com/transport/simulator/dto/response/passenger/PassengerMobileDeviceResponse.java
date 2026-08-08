package com.transport.simulator.dto.response.passenger;

import com.transport.simulator.entity.PassengerMobileDevice;
import com.transport.simulator.enums.PassengerDevicePlatform;
import com.transport.simulator.enums.PassengerMobileDeviceStatus;
import java.time.Instant;
import java.time.ZoneOffset;

public record PassengerMobileDeviceResponse(String deviceId, String installationId, String name,
        PassengerDevicePlatform platform, PassengerMobileDeviceStatus status,
        Instant registeredAt, Instant lastSeenAt) {
    public static PassengerMobileDeviceResponse from(PassengerMobileDevice device) {
        return new PassengerMobileDeviceResponse(device.getPublicId(), device.getInstallationId(),
                device.getDeviceName(), device.getPlatform(), device.getStatus(),
                device.getRegisteredAt().toInstant(ZoneOffset.UTC),
                device.getLastSeenAt().toInstant(ZoneOffset.UTC));
    }
}
