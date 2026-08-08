package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.request.passenger.PassengerDeviceRequest;
import com.transport.simulator.dto.request.passenger.PassengerLoginRequest;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.PassengerMobileDevice;
import com.transport.simulator.enums.PassengerDevicePlatform;
import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.PassengerMobileDeviceRepository;
import com.transport.simulator.repository.PassengerSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PassengerSessionServiceTests {
    private static final String INSTALLATION = "4a34d1a0-8257-4a41-8e9c-7d32752b9c42";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-08T10:00:00Z"), ZoneOffset.UTC);

    @Mock PassengerAccountRepository accountRepository;
    @Mock PassengerSessionRepository sessionRepository;
    @Mock PassengerMobileDeviceRepository deviceRepository;
    @Mock PasswordEncoder passwordEncoder;
    private PassengerSessionService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("non-existent-passenger-account")).thenReturn("dummy-hash");
        service = new PassengerSessionService(accountRepository, sessionRepository,
                deviceRepository, passwordEncoder, CLOCK, Duration.ofMinutes(15), Duration.ofDays(30));
    }

    @Test
    void shouldRegisterDeviceAndReplaceItsPreviousSessionWhenLoggingIn() {
        PassengerAccount account = account(10L, "passenger-a");
        when(accountRepository.findByEmailIgnoreCaseForUpdate("ana@example.local"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
        when(deviceRepository.findByInstallationIdForUpdate(INSTALLATION)).thenReturn(Optional.empty());
        when(deviceRepository.save(any(PassengerMobileDevice.class))).thenAnswer(invocation -> {
            PassengerMobileDevice device = invocation.getArgument(0);
            ReflectionTestUtils.setField(device, "id", 21L);
            return device;
        });
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.login(login());

        ArgumentCaptor<PassengerMobileDevice> device = ArgumentCaptor.forClass(PassengerMobileDevice.class);
        verify(deviceRepository).save(device.capture());
        assertThat(device.getValue().getPassengerAccount()).isSameAs(account);
        assertThat(device.getValue().getInstallationId()).isEqualTo(INSTALLATION);
        verify(sessionRepository).revokeAllActiveByMobileDeviceId(
                21L, java.time.LocalDateTime.of(2026, 8, 8, 10, 0), "DEVICE_REAUTHENTICATED");
        assertThat(response.user().publicId()).isEqualTo("passenger-a");
    }

    @Test
    void shouldRejectADeviceRegisteredByAnotherPassenger() {
        PassengerAccount account = account(10L, "passenger-a");
        PassengerMobileDevice foreignDevice = new PassengerMobileDevice(
                account(11L, "passenger-b"), INSTALLATION, "Pixel ajeno",
                PassengerDevicePlatform.ANDROID, java.time.LocalDateTime.now(CLOCK));
        when(accountRepository.findByEmailIgnoreCaseForUpdate("ana@example.local"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
        when(deviceRepository.findByInstallationIdForUpdate(INSTALLATION))
                .thenReturn(Optional.of(foreignDevice));

        assertThatThrownBy(() -> service.login(login()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidCredentialsWithoutRegisteringADevice() {
        when(accountRepository.findByEmailIgnoreCaseForUpdate("ana@example.local"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.matches("wrong-password", "dummy-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new PassengerLoginRequest(
                "ana@example.local", "wrong-password", login().device())))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(deviceRepository, never()).save(any());
    }

    private PassengerLoginRequest login() {
        return new PassengerLoginRequest(" ana@example.local ", "correct-password",
                new PassengerDeviceRequest(INSTALLATION, "Pixel 9", PassengerDevicePlatform.ANDROID));
    }

    private PassengerAccount account(Long id, String publicId) {
        PassengerAccount account = new PassengerAccount(publicId, "ana@example.local", "stored-hash", "Ana", "Red");
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
