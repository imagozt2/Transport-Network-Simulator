package com.transport.simulator.ticketing.qr;

import com.transport.simulator.entity.TicketQrCredential;
import com.transport.simulator.enums.TicketQrCredentialStatus;
import com.transport.simulator.repository.TicketQrCredentialRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class TicketQrVerifier {

    private static final Base64.Decoder BASE64_URL = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final TicketQrPayloadCodec payloadCodec;
    private final TicketQrVerificationKeyProvider keyProvider;
    private final TicketQrCredentialRepository credentialRepository;
    private final TicketQrSigningProperties properties;
    private final Clock clock;

    public TicketQrVerifier(
            ObjectMapper objectMapper,
            TicketQrPayloadCodec payloadCodec,
            TicketQrVerificationKeyProvider keyProvider,
            TicketQrCredentialRepository credentialRepository,
            TicketQrSigningProperties properties,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.payloadCodec = payloadCodec;
        this.keyProvider = keyProvider;
        this.credentialRepository = credentialRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public VerifiedTicketQr verify(String qrValue) {
        String compactJws = extractCompactJws(qrValue);
        String[] segments = compactJws.split("\\.", -1);
        if (segments.length != 3 || segments[0].isEmpty() || segments[1].isEmpty() || segments[2].isEmpty()) {
            throw failure(TicketQrVerificationFailure.MALFORMED_QR);
        }

        TicketQrProtectedHeader header = decodeHeader(segments[0]);
        verifySignature(segments, header.keyId());
        TicketQrPayload payload = decodePayload(segments[1]);
        verifyTemporalClaims(payload);

        String fingerprint = sha256Hex(qrValue);
        TicketQrCredential credential = credentialRepository
                .findForVerificationByCredentialId(payload.credentialId())
                .orElseThrow(() -> failure(TicketQrVerificationFailure.CREDENTIAL_NOT_FOUND));
        verifyPersistedCredential(credential, payload, header.keyId(), fingerprint);

        return new VerifiedTicketQr(payload, credential, header.keyId(), fingerprint);
    }

    private String extractCompactJws(String qrValue) {
        int maximumLength = properties.maximumQrLength() > 0 ? properties.maximumQrLength() : 4096;
        if (qrValue == null || qrValue.isBlank() || qrValue.length() > maximumLength) {
            throw failure(TicketQrVerificationFailure.MALFORMED_QR);
        }
        if (!qrValue.startsWith("RMM:TICKET:")) {
            throw failure(TicketQrVerificationFailure.MALFORMED_QR);
        }
        if (!qrValue.startsWith(TicketQrContract.WRAPPER_PREFIX)) {
            throw failure(TicketQrVerificationFailure.UNSUPPORTED_VERSION);
        }
        return qrValue.substring(TicketQrContract.WRAPPER_PREFIX.length());
    }

    private TicketQrProtectedHeader decodeHeader(String encodedHeader) {
        try {
            TicketQrProtectedHeader header = objectMapper.readValue(
                    BASE64_URL.decode(encodedHeader),
                    TicketQrProtectedHeader.class
            );
            if (!TicketQrContract.JWS_ALGORITHM.equals(header.algorithm())
                    || !TicketQrContract.JWS_TYPE.equals(header.type())) {
                throw failure(TicketQrVerificationFailure.INVALID_HEADER);
            }
            return header;
        } catch (TicketQrVerificationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TicketQrVerificationException(TicketQrVerificationFailure.INVALID_HEADER, exception);
        }
    }

    private void verifySignature(String[] segments, String keyId) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(keyProvider.findTrustedKey(keyId));
            signature.update((segments[0] + "." + segments[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(BASE64_URL.decode(segments[2]))) {
                throw failure(TicketQrVerificationFailure.INVALID_SIGNATURE);
            }
        } catch (TicketQrVerificationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TicketQrVerificationException(TicketQrVerificationFailure.INVALID_SIGNATURE, exception);
        }
    }

    private TicketQrPayload decodePayload(String encodedPayload) {
        try {
            return payloadCodec.decode(BASE64_URL.decode(encodedPayload));
        } catch (Exception exception) {
            throw new TicketQrVerificationException(TicketQrVerificationFailure.INVALID_PAYLOAD, exception);
        }
    }

    private void verifyTemporalClaims(TicketQrPayload payload) {
        long skew = Math.max(0, properties.allowedClockSkewSeconds());
        long now = clock.instant().getEpochSecond();
        if (payload.issuedAtEpochSecond() > now + skew) {
            throw failure(TicketQrVerificationFailure.NOT_YET_VALID);
        }
        if (payload.expiresAtEpochSecond() != null && payload.expiresAtEpochSecond() < now - skew) {
            throw failure(TicketQrVerificationFailure.EXPIRED);
        }
    }

    private void verifyPersistedCredential(
            TicketQrCredential credential,
            TicketQrPayload payload,
            String keyId,
            String fingerprint
    ) {
        if (credential.getStatus() == TicketQrCredentialStatus.REVOKED) {
            throw failure(TicketQrVerificationFailure.CREDENTIAL_REVOKED);
        }
        if (credential.getStatus() == TicketQrCredentialStatus.SUPERSEDED) {
            throw failure(TicketQrVerificationFailure.CREDENTIAL_SUPERSEDED);
        }
        if (credential.getStatus() == TicketQrCredentialStatus.EXPIRED) {
            throw failure(TicketQrVerificationFailure.EXPIRED);
        }
        if (credential.getExpiresAt() != null
                && credential.getExpiresAt().toInstant(ZoneOffset.UTC).isBefore(clock.instant())) {
            throw failure(TicketQrVerificationFailure.EXPIRED);
        }
        Long persistedExpiry = credential.getExpiresAt() == null
                ? null
                : credential.getExpiresAt().toEpochSecond(ZoneOffset.UTC);
        if (credential.getStatus() != TicketQrCredentialStatus.ACTIVE
                || credential.getWrapperVersion() != TicketQrContract.WRAPPER_VERSION
                || !credential.getSigningKeyId().equals(keyId)
                || !MessageDigest.isEqual(
                        credential.getTokenFingerprint().getBytes(StandardCharsets.US_ASCII),
                        fingerprint.getBytes(StandardCharsets.US_ASCII)
                )
                || !credential.getTicket().getCode().equals(payload.ticketCode())
                || !credential.getSupport().getTicket().getId().equals(credential.getTicket().getId())
                || credential.getSupport().getType() != payload.medium()
                || credential.getIssuedAt().toEpochSecond(ZoneOffset.UTC) != payload.issuedAtEpochSecond()
                || !java.util.Objects.equals(persistedExpiry, payload.expiresAtEpochSecond())) {
            throw failure(TicketQrVerificationFailure.CREDENTIAL_INCONSISTENT);
        }
    }

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new TicketQrVerificationException(TicketQrVerificationFailure.INVALID_PAYLOAD, exception);
        }
    }

    private TicketQrVerificationException failure(TicketQrVerificationFailure failure) {
        return new TicketQrVerificationException(failure);
    }
}
