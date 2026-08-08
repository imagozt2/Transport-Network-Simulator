package com.transport.simulator.ticketing.qr;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TicketQrSigningKeyProvider {

    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

    private final TicketQrSigningProperties properties;

    public TicketQrSigningKeyProvider(TicketQrSigningProperties properties) {
        this.properties = properties;
    }

    public String activeKeyId() {
        if (!StringUtils.hasText(properties.keyId())) {
            throw new TicketQrSigningException("No active ticket QR signing key id is configured");
        }
        return properties.keyId().trim();
    }

    public PrivateKey activePrivateKey() {
        if (!StringUtils.hasText(properties.privateKey())) {
            throw new TicketQrSigningException("No ticket QR private signing key is configured");
        }

        try {
            String encodedKey = properties.privateKey()
                    .replace(PRIVATE_KEY_BEGIN, "")
                    .replace(PRIVATE_KEY_END, "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            return KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new TicketQrSigningException("The configured ticket QR private key is not a valid Ed25519 PKCS#8 key", exception);
        }
    }
}
