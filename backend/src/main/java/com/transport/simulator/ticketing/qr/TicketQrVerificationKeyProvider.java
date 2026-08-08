package com.transport.simulator.ticketing.qr;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TicketQrVerificationKeyProvider {

    private static final String PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_KEY_END = "-----END PUBLIC KEY-----";

    private final TicketQrSigningProperties properties;

    public TicketQrVerificationKeyProvider(TicketQrSigningProperties properties) {
        this.properties = properties;
    }

    public PublicKey findTrustedKey(String keyId) {
        if (!StringUtils.hasText(keyId) || !keyId.equals(properties.keyId())) {
            throw new TicketQrVerificationException(TicketQrVerificationFailure.UNTRUSTED_KEY);
        }
        if (!StringUtils.hasText(properties.publicKey())) {
            throw new TicketQrVerificationException(TicketQrVerificationFailure.VERIFICATION_NOT_CONFIGURED);
        }

        try {
            String encodedKey = properties.publicKey()
                    .replace(PUBLIC_KEY_BEGIN, "")
                    .replace(PUBLIC_KEY_END, "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new TicketQrVerificationException(
                    TicketQrVerificationFailure.VERIFICATION_NOT_CONFIGURED,
                    exception
            );
        }
    }
}
