package com.transport.simulator.ticketing.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketQrCredential;
import com.transport.simulator.entity.TicketQrUseClaim;
import com.transport.simulator.entity.TicketSupport;
import com.transport.simulator.enums.TicketQrCredentialStatus;
import com.transport.simulator.enums.TicketQrValidationType;
import com.transport.simulator.enums.TicketSupportType;
import com.transport.simulator.repository.TicketQrCredentialRepository;
import com.transport.simulator.repository.TicketQrUseClaimRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TicketQrSecurityTests {

    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
    private static final String KEY_ID = "rmm-ticket-2026-01";
    private static final UUID CREDENTIAL_ID = UUID.fromString("5d14bed4-c72f-49e8-bb99-cb61de7255c0");

    private TicketQrCredentialRepository credentialRepository;
    private TicketQrUseClaimRepository claimRepository;
    private TicketQrSigner signer;
    private TicketQrVerifier verifier;
    private Clock clock;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        TicketQrSigningProperties properties = new TicketQrSigningProperties(
                KEY_ID,
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                "",
                60,
                4096
        );
        ObjectMapper objectMapper = new ObjectMapper();
        TicketQrPayloadCodec payloadCodec = new TicketQrPayloadCodec(objectMapper);
        TicketQrKeyRing keyRing = new TicketQrKeyRing(properties);
        signer = new TicketQrSigner(
                objectMapper,
                payloadCodec,
                new TicketQrSigningKeyProvider(keyRing)
        );
        credentialRepository = mock(TicketQrCredentialRepository.class);
        claimRepository = mock(TicketQrUseClaimRepository.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        verifier = new TicketQrVerifier(
                objectMapper,
                payloadCodec,
                new TicketQrVerificationKeyProvider(keyRing),
                credentialRepository,
                properties,
                clock
        );
    }

    @Test
    void shouldVerifyAnUntamperedActiveQr() {
        TicketQrPayload payload = payload(NOW.minusSeconds(30), NOW.plusSeconds(300));
        SignedTicketQr signedQr = signer.sign(payload);
        TicketQrCredential credential = credential(payload, signedQr, TicketQrCredentialStatus.ACTIVE);
        when(credentialRepository.findForVerificationByCredentialId(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));

        VerifiedTicketQr verified = verifier.verify(signedQr.value());

        assertThat(verified.payload()).isEqualTo(payload);
        assertThat(verified.credential()).isSameAs(credential);
        assertThat(verified.keyId()).isEqualTo(KEY_ID);
        assertThat(verified.fingerprint()).isEqualTo(signedQr.fingerprint());
    }

    @Test
    void shouldRejectPayloadManipulationBeforeConsultingItsState() {
        SignedTicketQr signedQr = signer.sign(payload(NOW.minusSeconds(30), NOW.plusSeconds(300)));
        String[] segments = signedQr.compactJws().split("\\.");
        char replacement = segments[1].charAt(0) == 'A' ? 'B' : 'A';
        segments[1] = replacement + segments[1].substring(1);
        String manipulated = TicketQrContract.WRAPPER_PREFIX + String.join(".", segments);

        assertVerificationFailure(manipulated, TicketQrVerificationFailure.INVALID_SIGNATURE);
    }

    @Test
    void shouldRejectExpiredAndRevokedCredentials() {
        SignedTicketQr expiredQr = signer.sign(payload(NOW.minusSeconds(600), NOW.minusSeconds(120)));
        assertVerificationFailure(expiredQr.value(), TicketQrVerificationFailure.EXPIRED);

        TicketQrPayload activePayload = payload(NOW.minusSeconds(30), NOW.plusSeconds(300));
        SignedTicketQr revokedQr = signer.sign(activePayload);
        TicketQrCredential revokedCredential = credential(
                activePayload,
                revokedQr,
                TicketQrCredentialStatus.REVOKED
        );
        when(credentialRepository.findForVerificationByCredentialId(CREDENTIAL_ID))
                .thenReturn(Optional.of(revokedCredential));
        assertVerificationFailure(revokedQr.value(), TicketQrVerificationFailure.CREDENTIAL_REVOKED);
    }

    @Test
    void shouldReturnTheSameClaimForAnIdenticalRetryAndRejectChangedData() {
        TicketQrCredential credential = mock(TicketQrCredential.class);
        when(credential.getId()).thenReturn(42L);
        when(credential.getCredentialId()).thenReturn(CREDENTIAL_ID);
        when(credentialRepository.lockById(42L)).thenReturn(Optional.of(credential));

        AtomicReference<TicketQrUseClaim> persisted = new AtomicReference<>();
        when(claimRepository.findByValidationReference("VAL-2026-000001"))
                .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        when(claimRepository.save(any(TicketQrUseClaim.class))).thenAnswer(invocation -> {
            TicketQrUseClaim claim = invocation.getArgument(0);
            persisted.set(claim);
            return claim;
        });
        TicketQrUseGuard guard = new TicketQrUseGuard(credentialRepository, claimRepository, clock);
        VerifiedTicketQr verifiedQr = new VerifiedTicketQr(
                payload(NOW.minusSeconds(30), NOW.plusSeconds(300)),
                credential,
                KEY_ID,
                "a".repeat(64)
        );

        TicketQrUseClaimResult first = guard.claim(
                verifiedQr,
                "VAL-2026-000001",
                TicketQrValidationType.ENTRY,
                "RMM-EN-ST038-01",
                "ST038"
        );
        TicketQrUseClaimResult retry = guard.claim(
                verifiedQr,
                "VAL-2026-000001",
                TicketQrValidationType.ENTRY,
                "RMM-EN-ST038-01",
                "ST038"
        );

        assertThat(first.outcome()).isEqualTo(TicketQrUseClaimOutcome.NEW);
        assertThat(retry.outcome()).isEqualTo(TicketQrUseClaimOutcome.IDEMPOTENT_RETRY);
        assertThat(retry.claim()).isSameAs(first.claim());
        assertThatThrownBy(() -> guard.claim(
                verifiedQr,
                "VAL-2026-000001",
                TicketQrValidationType.ENTRY,
                "RMM-EN-ST038-01",
                "ST039"
        )).isInstanceOf(TicketQrReferenceReuseException.class);
    }

    private TicketQrPayload payload(Instant issuedAt, Instant expiresAt) {
        return new TicketQrPayload(
                TicketQrContract.PAYLOAD_VERSION,
                TicketQrContract.ISSUER,
                TicketQrContract.AUDIENCE,
                CREDENTIAL_ID,
                "RMM-TKT-SECURITY-001",
                TicketSupportType.DIGITAL,
                issuedAt.getEpochSecond(),
                expiresAt.getEpochSecond()
        );
    }

    private TicketQrCredential credential(
            TicketQrPayload payload,
            SignedTicketQr signedQr,
            TicketQrCredentialStatus status
    ) {
        Ticket ticket = mock(Ticket.class);
        TicketSupport support = mock(TicketSupport.class);
        TicketQrCredential credential = mock(TicketQrCredential.class);
        when(ticket.getId()).thenReturn(7L);
        when(ticket.getCode()).thenReturn(payload.ticketCode());
        when(support.getTicket()).thenReturn(ticket);
        when(support.getType()).thenReturn(payload.medium());
        when(credential.getId()).thenReturn(42L);
        when(credential.getCredentialId()).thenReturn(payload.credentialId());
        when(credential.getTicket()).thenReturn(ticket);
        when(credential.getSupport()).thenReturn(support);
        when(credential.getStatus()).thenReturn(status);
        when(credential.getWrapperVersion()).thenReturn(TicketQrContract.WRAPPER_VERSION);
        when(credential.getSigningKeyId()).thenReturn(KEY_ID);
        when(credential.getTokenFingerprint()).thenReturn(signedQr.fingerprint());
        when(credential.getIssuedAt()).thenReturn(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(payload.issuedAtEpochSecond()),
                ZoneOffset.UTC
        ));
        when(credential.getExpiresAt()).thenReturn(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(payload.expiresAtEpochSecond()),
                ZoneOffset.UTC
        ));
        return credential;
    }

    private void assertVerificationFailure(String qrValue, TicketQrVerificationFailure expected) {
        assertThatThrownBy(() -> verifier.verify(qrValue))
                .isInstanceOfSatisfying(TicketQrVerificationException.class, exception ->
                        assertThat(exception.getFailure()).isEqualTo(expected));
    }
}
