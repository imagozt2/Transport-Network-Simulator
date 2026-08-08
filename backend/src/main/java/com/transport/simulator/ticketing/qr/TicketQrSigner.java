package com.transport.simulator.ticketing.qr;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TicketQrSigner {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final ObjectMapper objectMapper;
    private final TicketQrPayloadCodec payloadCodec;
    private final TicketQrSigningKeyProvider keyProvider;

    public TicketQrSigner(
            ObjectMapper objectMapper,
            TicketQrPayloadCodec payloadCodec,
            TicketQrSigningKeyProvider keyProvider
    ) {
        this.objectMapper = objectMapper;
        this.payloadCodec = payloadCodec;
        this.keyProvider = keyProvider;
    }

    public SignedTicketQr sign(TicketQrPayload payload) {
        String keyId = keyProvider.activeKeyId();
        TicketQrProtectedHeader header = new TicketQrProtectedHeader(
                TicketQrContract.JWS_ALGORITHM,
                keyId,
                TicketQrContract.JWS_TYPE
        );

        String encodedHeader = BASE64_URL.encodeToString(encodeHeader(header));
        String encodedPayload = BASE64_URL.encodeToString(payloadCodec.encode(payload));
        String signingInput = encodedHeader + "." + encodedPayload;
        String encodedSignature = BASE64_URL.encodeToString(sign(signingInput));
        String compactJws = signingInput + "." + encodedSignature;
        String qrValue = TicketQrContract.WRAPPER_PREFIX + compactJws;

        return new SignedTicketQr(qrValue, compactJws, keyId, sha256Hex(qrValue));
    }

    private byte[] encodeHeader(TicketQrProtectedHeader header) {
        try {
            return objectMapper.writeValueAsBytes(header);
        } catch (JacksonException exception) {
            throw new TicketQrSigningException("Ticket QR protected header could not be encoded", exception);
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(keyProvider.activePrivateKey());
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signature.sign();
        } catch (TicketQrSigningException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TicketQrSigningException("Ticket QR could not be signed", exception);
        }
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new TicketQrSigningException("Ticket QR fingerprint could not be calculated", exception);
        }
    }
}
