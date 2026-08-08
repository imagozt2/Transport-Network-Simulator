package com.transport.simulator.ticketing.qr;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TicketQrKeyRing {

    private static final Pattern KEY_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$");
    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";
    private static final String PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_KEY_END = "-----END PUBLIC KEY-----";

    private final TicketQrSigningProperties properties;

    public TicketQrKeyRing(TicketQrSigningProperties properties) {
        this.properties = properties;
    }

    public String activeKeyId() {
        return validateKeyId(properties.keyId(), "No active ticket QR signing key id is configured");
    }

    public PrivateKey activePrivateKey() {
        if (!StringUtils.hasText(properties.privateKey())) {
            throw new TicketQrSigningException("No ticket QR private signing key is configured");
        }
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(
                    new PKCS8EncodedKeySpec(decodeKey(properties.privateKey(), PRIVATE_KEY_BEGIN, PRIVATE_KEY_END))
            );
        } catch (Exception exception) {
            throw new TicketQrSigningException(
                    "The configured ticket QR private key is not a valid Ed25519 PKCS#8 key",
                    exception
            );
        }
    }

    public PublicKey trustedPublicKey(String requestedKeyId) {
        String keyId = validateVerificationKeyId(requestedKeyId);
        String encodedKey = trustedEncodedPublicKeys().get(keyId);
        if (!StringUtils.hasText(encodedKey)) {
            throw new TicketQrVerificationException(TicketQrVerificationFailure.UNTRUSTED_KEY);
        }
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(
                    new X509EncodedKeySpec(decodeKey(encodedKey, PUBLIC_KEY_BEGIN, PUBLIC_KEY_END))
            );
        } catch (Exception exception) {
            throw new TicketQrVerificationException(
                    TicketQrVerificationFailure.VERIFICATION_NOT_CONFIGURED,
                    exception
            );
        }
    }

    public List<TicketQrKeyDescriptor> trustedKeys() {
        String activeKeyId = activeKeyId();
        List<TicketQrKeyDescriptor> keys = new ArrayList<>();
        trustedEncodedPublicKeys().keySet().forEach(keyId -> keys.add(new TicketQrKeyDescriptor(
                keyId,
                keyId.equals(activeKeyId) ? TicketQrKeyStatus.ACTIVE : TicketQrKeyStatus.RETIRED
        )));
        return List.copyOf(keys);
    }

    private Map<String, String> trustedEncodedPublicKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        if (StringUtils.hasText(properties.publicKey())) {
            keys.put(activeKeyId(), properties.publicKey().trim());
        }
        if (StringUtils.hasText(properties.retiredPublicKeys())) {
            for (String entry : properties.retiredPublicKeys().split(";")) {
                if (entry.isBlank()) {
                    continue;
                }
                int separator = entry.indexOf('=');
                if (separator <= 0 || separator == entry.length() - 1) {
                    throw new TicketQrVerificationException(
                            TicketQrVerificationFailure.VERIFICATION_NOT_CONFIGURED
                    );
                }
                String keyId = validateVerificationKeyId(entry.substring(0, separator).trim());
                if (keys.putIfAbsent(keyId, entry.substring(separator + 1).trim()) != null) {
                    throw new TicketQrVerificationException(
                            TicketQrVerificationFailure.VERIFICATION_NOT_CONFIGURED
                    );
                }
            }
        }
        return keys;
    }

    private String validateVerificationKeyId(String keyId) {
        if (!StringUtils.hasText(keyId) || !KEY_ID_PATTERN.matcher(keyId.trim()).matches()) {
            throw new TicketQrVerificationException(TicketQrVerificationFailure.UNTRUSTED_KEY);
        }
        return keyId.trim();
    }

    private String validateKeyId(String keyId, String message) {
        if (!StringUtils.hasText(keyId) || !KEY_ID_PATTERN.matcher(keyId.trim()).matches()) {
            throw new TicketQrSigningException(message);
        }
        return keyId.trim();
    }

    private byte[] decodeKey(String value, String beginMarker, String endMarker) {
        String encodedKey = value
                .replace(beginMarker, "")
                .replace(endMarker, "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(encodedKey);
    }
}
