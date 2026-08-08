package com.transport.simulator.service;

import com.transport.simulator.dto.response.passenger.PassengerMobileDeviceResponse;
import com.transport.simulator.entity.PassengerMobileDevice;
import com.transport.simulator.repository.PassengerMobileDeviceRepository;
import com.transport.simulator.repository.PassengerSessionRepository;
import com.transport.simulator.security.PassengerPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PassengerMobileDeviceService {
    private final PassengerMobileDeviceRepository deviceRepository;
    private final PassengerSessionRepository sessionRepository;
    private final Clock clock;

    public PassengerMobileDeviceService(PassengerMobileDeviceRepository deviceRepository,
            PassengerSessionRepository sessionRepository, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PassengerMobileDeviceResponse> devices(Authentication authentication) {
        PassengerPrincipal principal = principal(authentication);
        return deviceRepository.findAllByPassengerAccountIdOrderByLastSeenAtDesc(principal.accountId())
                .stream().map(PassengerMobileDeviceResponse::from).toList();
    }

    @Transactional
    public void revoke(String publicId, Authentication authentication) {
        PassengerPrincipal principal = principal(authentication);
        PassengerMobileDevice device = deviceRepository.findOwnedByPublicIdForUpdate(
                publicId == null ? "" : publicId.trim(), principal.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Passenger mobile device not found"));
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        device.revoke(now);
        sessionRepository.revokeAllActiveByMobileDeviceId(device.getId(), now, "DEVICE_REVOKED");
    }

    private PassengerPrincipal principal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PassengerPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid passenger session");
        }
        return principal;
    }
}
