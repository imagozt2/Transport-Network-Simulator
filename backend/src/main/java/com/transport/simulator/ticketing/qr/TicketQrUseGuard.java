package com.transport.simulator.ticketing.qr;

import com.transport.simulator.entity.TicketQrCredential;
import com.transport.simulator.entity.TicketQrUseClaim;
import com.transport.simulator.enums.TicketQrValidationType;
import com.transport.simulator.repository.TicketQrCredentialRepository;
import com.transport.simulator.repository.TicketQrUseClaimRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketQrUseGuard {

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,149}$");
    private static final Pattern DEVICE_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$");
    private static final Pattern STATION_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,19}$");

    private final TicketQrCredentialRepository credentialRepository;
    private final TicketQrUseClaimRepository claimRepository;
    private final Clock clock;

    public TicketQrUseGuard(
            TicketQrCredentialRepository credentialRepository,
            TicketQrUseClaimRepository claimRepository,
            Clock clock
    ) {
        this.credentialRepository = credentialRepository;
        this.claimRepository = claimRepository;
        this.clock = clock;
    }

    @Transactional
    public TicketQrUseClaimResult claim(
            VerifiedTicketQr verifiedQr,
            String validationReference,
            TicketQrValidationType validationType,
            String deviceCode,
            String stationCode
    ) {
        Objects.requireNonNull(verifiedQr, "verifiedQr is required");
        String reference = requireMatch(validationReference, REFERENCE_PATTERN, "validationReference");
        String normalizedDevice = requireMatch(deviceCode, DEVICE_CODE_PATTERN, "deviceCode");
        String normalizedStation = requireMatch(stationCode, STATION_CODE_PATTERN, "stationCode");
        Objects.requireNonNull(validationType, "validationType is required");

        TicketQrCredential credential = credentialRepository.lockById(verifiedQr.credential().getId())
                .orElseThrow(() -> new TicketQrVerificationException(
                        TicketQrVerificationFailure.CREDENTIAL_NOT_FOUND
                ));
        String fingerprint = requestFingerprint(
                credential,
                validationType,
                normalizedDevice,
                normalizedStation
        );

        return claimRepository.findByValidationReference(reference)
                .map(existing -> existingClaim(existing, credential, fingerprint))
                .orElseGet(() -> newClaim(
                        reference,
                        credential,
                        validationType,
                        normalizedDevice,
                        normalizedStation,
                        fingerprint
                ));
    }

    @Transactional
    public TicketQrUseClaim complete(String validationReference) {
        TicketQrUseClaim claim = claimRepository.findByValidationReference(validationReference)
                .orElseThrow(() -> new IllegalArgumentException("Unknown validation reference"));
        claim.complete(LocalDateTime.now(clock));
        return claim;
    }

    private TicketQrUseClaimResult existingClaim(
            TicketQrUseClaim existing,
            TicketQrCredential credential,
            String fingerprint
    ) {
        if (!existing.getCredential().getId().equals(credential.getId())
                || !MessageDigest.isEqual(
                        existing.getRequestFingerprint().getBytes(StandardCharsets.US_ASCII),
                        fingerprint.getBytes(StandardCharsets.US_ASCII)
                )) {
            throw new TicketQrReferenceReuseException();
        }
        return new TicketQrUseClaimResult(TicketQrUseClaimOutcome.IDEMPOTENT_RETRY, existing);
    }

    private TicketQrUseClaimResult newClaim(
            String reference,
            TicketQrCredential credential,
            TicketQrValidationType validationType,
            String deviceCode,
            String stationCode,
            String fingerprint
    ) {
        TicketQrUseClaim claim = claimRepository.save(new TicketQrUseClaim(
                reference,
                credential,
                validationType,
                deviceCode,
                stationCode,
                fingerprint,
                LocalDateTime.now(clock)
        ));
        return new TicketQrUseClaimResult(TicketQrUseClaimOutcome.NEW, claim);
    }

    private String requestFingerprint(
            TicketQrCredential credential,
            TicketQrValidationType validationType,
            String deviceCode,
            String stationCode
    ) {
        String canonicalRequest = credential.getCredentialId() + "\n"
                + validationType.name() + "\n" + deviceCode + "\n" + stationCode;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("QR validation request fingerprint could not be calculated", exception);
        }
    }

    private String requireMatch(String value, Pattern pattern, String field) {
        if (value == null || !pattern.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value.trim();
    }
}
