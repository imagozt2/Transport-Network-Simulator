package com.transport.simulator.ticketing.qr;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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
    private static final String LOCAL_KEY_ID = "rmm-local-ticket-qr-1";
    private static final String KEY_ID_FILE = "key-id.txt";
    private static final String PRIVATE_KEY_FILE = "private-key.pkcs8";
    private static final String PUBLIC_KEY_FILE = "public-key.x509";

    private final TicketQrSigningProperties properties;
    private volatile LocalKeyMaterial localKeyMaterial;

    public TicketQrKeyRing(TicketQrSigningProperties properties) {
        this.properties = properties;
    }

    public String activeKeyId() {
        if (hasExplicitKeyConfiguration()) {
            requireCompleteExplicitConfiguration();
            return validateKeyId(properties.keyId(), "No active ticket QR signing key id is configured");
        }
        return localKeyMaterial().keyId();
    }

    public PrivateKey activePrivateKey() {
        String encodedPrivateKey = hasExplicitKeyConfiguration()
                ? explicitPrivateKey() : localKeyMaterial().privateKey();
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(
                    new PKCS8EncodedKeySpec(decodeKey(encodedPrivateKey, PRIVATE_KEY_BEGIN, PRIVATE_KEY_END))
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
        if (hasExplicitKeyConfiguration()) {
            requireCompleteExplicitConfiguration();
            keys.put(activeKeyId(), properties.publicKey().trim());
        } else if (hasLocalKeyDirectory()) {
            LocalKeyMaterial local = localKeyMaterial();
            keys.put(local.keyId(), local.publicKey());
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

    private String explicitPrivateKey() {
        requireCompleteExplicitConfiguration();
        return properties.privateKey();
    }

    private boolean hasExplicitKeyConfiguration() {
        return StringUtils.hasText(properties.keyId())
                || StringUtils.hasText(properties.privateKey())
                || StringUtils.hasText(properties.publicKey());
    }

    private void requireCompleteExplicitConfiguration() {
        if (!StringUtils.hasText(properties.keyId())
                || !StringUtils.hasText(properties.privateKey())
                || !StringUtils.hasText(properties.publicKey())) {
            throw new TicketQrSigningException(
                    "Ticket QR signing key id, private key and public key must be configured together");
        }
    }

    private boolean hasLocalKeyDirectory() {
        return StringUtils.hasText(properties.localKeyDirectory());
    }

    private LocalKeyMaterial localKeyMaterial() {
        LocalKeyMaterial cached = localKeyMaterial;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (localKeyMaterial == null) {
                localKeyMaterial = loadOrCreateLocalKeyMaterial();
            }
            return localKeyMaterial;
        }
    }

    private LocalKeyMaterial loadOrCreateLocalKeyMaterial() {
        if (!hasLocalKeyDirectory()) {
            throw new TicketQrSigningException("No ticket QR signing key is configured");
        }
        try {
            Path directory = Path.of(properties.localKeyDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path keyIdPath = directory.resolve(KEY_ID_FILE);
            Path privateKeyPath = directory.resolve(PRIVATE_KEY_FILE);
            Path publicKeyPath = directory.resolve(PUBLIC_KEY_FILE);
            boolean keyIdExists = Files.isRegularFile(keyIdPath);
            boolean privateKeyExists = Files.isRegularFile(privateKeyPath);
            boolean publicKeyExists = Files.isRegularFile(publicKeyPath);
            if (keyIdExists && privateKeyExists && publicKeyExists) {
                return new LocalKeyMaterial(
                        validateKeyId(Files.readString(keyIdPath).trim(), "Invalid local ticket QR key id"),
                        Files.readString(privateKeyPath).trim(),
                        Files.readString(publicKeyPath).trim());
            }
            if (keyIdExists || privateKeyExists || publicKeyExists) {
                throw new TicketQrSigningException("The local ticket QR key store is incomplete");
            }
            KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            LocalKeyMaterial generated = new LocalKeyMaterial(
                    LOCAL_KEY_ID,
                    Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                    Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
            Files.writeString(keyIdPath, generated.keyId(), StandardCharsets.US_ASCII);
            Files.writeString(privateKeyPath, generated.privateKey(), StandardCharsets.US_ASCII);
            Files.writeString(publicKeyPath, generated.publicKey(), StandardCharsets.US_ASCII);
            return generated;
        } catch (TicketQrSigningException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TicketQrSigningException("The local ticket QR key store could not be prepared", exception);
        }
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

    private record LocalKeyMaterial(String keyId, String privateKey, String publicKey) {
    }
}
