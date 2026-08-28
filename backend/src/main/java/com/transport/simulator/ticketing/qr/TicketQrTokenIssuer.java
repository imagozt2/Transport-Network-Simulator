package com.transport.simulator.ticketing.qr;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class TicketQrTokenIssuer {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom;

    public TicketQrTokenIssuer() {
        this(new SecureRandom());
    }

    TicketQrTokenIssuer(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public CompactTicketQr issue() {
        byte[] token = new byte[TicketQrContract.OPAQUE_TOKEN_BYTES];
        secureRandom.nextBytes(token);
        String value = TicketQrContract.WRAPPER_PREFIX + BASE64_URL.encodeToString(token);
        return new CompactTicketQr(value, fingerprint(value));
    }

    static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new TicketQrSigningException("Ticket QR fingerprint could not be calculated", exception);
        }
    }
}
