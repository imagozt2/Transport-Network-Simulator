package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.PassengerMobileDevice;
import com.transport.simulator.enums.PassengerDevicePlatform;
import com.transport.simulator.enums.PassengerMobileDeviceStatus;
import com.transport.simulator.repository.PassengerMobileDeviceRepository;
import com.transport.simulator.repository.PassengerSessionRepository;
import com.transport.simulator.security.PassengerPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PassengerMobileDeviceServiceTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-08T10:00:00Z"), ZoneOffset.UTC);
    @Mock PassengerMobileDeviceRepository deviceRepository;
    @Mock PassengerSessionRepository sessionRepository;

    @Test
    void shouldRevokeOwnedDeviceAndAllItsActiveSessions() {
        PassengerMobileDevice device = device(10L, 20L);
        when(deviceRepository.findOwnedByPublicIdForUpdate("device-public-id", 10L))
                .thenReturn(Optional.of(device));
        PassengerMobileDeviceService service = new PassengerMobileDeviceService(
                deviceRepository, sessionRepository, CLOCK);

        service.revoke("device-public-id", authentication(10L));

        assertThat(device.getStatus()).isEqualTo(PassengerMobileDeviceStatus.REVOKED);
        verify(sessionRepository).revokeAllActiveByMobileDeviceId(
                20L, LocalDateTime.of(2026, 8, 8, 10, 0), "DEVICE_REVOKED");
    }

    @Test
    void shouldNotRevealOrRevokeAnotherPassengersDevice() {
        when(deviceRepository.findOwnedByPublicIdForUpdate("foreign-device", 10L))
                .thenReturn(Optional.empty());
        PassengerMobileDeviceService service = new PassengerMobileDeviceService(
                deviceRepository, sessionRepository, CLOCK);

        assertThatThrownBy(() -> service.revoke("foreign-device", authentication(10L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(sessionRepository, never()).revokeAllActiveByMobileDeviceId(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private PassengerMobileDevice device(Long accountId, Long deviceId) {
        PassengerAccount account = new PassengerAccount("passenger-a", "ana@example.local",
                "hash", "Ana", "Red");
        ReflectionTestUtils.setField(account, "id", accountId);
        PassengerMobileDevice device = new PassengerMobileDevice(account,
                "4a34d1a0-8257-4a41-8e9c-7d32752b9c42", "Pixel 9",
                PassengerDevicePlatform.ANDROID, LocalDateTime.now(CLOCK));
        ReflectionTestUtils.setField(device, "id", deviceId);
        ReflectionTestUtils.setField(device, "publicId", "device-public-id");
        return device;
    }

    private UsernamePasswordAuthenticationToken authentication(Long accountId) {
        return new UsernamePasswordAuthenticationToken(
                new PassengerPrincipal(accountId, "passenger-a", 30L, "installation-a"),
                null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_PASSENGER")));
    }
}
