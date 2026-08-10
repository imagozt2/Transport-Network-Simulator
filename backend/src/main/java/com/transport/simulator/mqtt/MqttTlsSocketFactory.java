package com.transport.simulator.mqtt;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

final class MqttTlsSocketFactory {
    private static final char[] KEY_PASSWORD = new char[0];

    private MqttTlsSocketFactory() {}

    static SSLSocketFactory create(MqttClientProperties properties) {
        try {
            X509Certificate authority = certificate(properties.caCertificate());
            X509Certificate clientCertificate = certificate(properties.clientCertificate());
            PrivateKey privateKey = privateKey(properties.clientPrivateKey());

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("rmm-mqtt-ca", authority);
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setKeyEntry("rmm-backend", privateKey, KEY_PASSWORD,
                    new java.security.cert.Certificate[]{clientCertificate, authority});
            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, KEY_PASSWORD);

            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
            return context.getSocketFactory();
        } catch (Exception exception) {
            throw new IllegalStateException("MQTT TLS material could not be loaded", exception);
        }
    }

    private static X509Certificate certificate(String path) throws Exception {
        try (var input = Files.newInputStream(Path.of(path))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
        }
    }

    private static PrivateKey privateKey(String path) throws Exception {
        String pem;
        try (Reader reader = Files.newBufferedReader(Path.of(path), StandardCharsets.US_ASCII)) {
            StringBuilder value = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) >= 0) value.append(buffer, 0, read);
            pem = value.toString();
        }
        String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] bytes = Base64.getDecoder().decode(encoded);
        PKCS8EncodedKeySpec specification = new PKCS8EncodedKeySpec(bytes);
        for (String algorithm : new String[]{"RSA", "EC", "Ed25519"}) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(specification);
            } catch (java.security.GeneralSecurityException ignored) {
                // Try the next supported key algorithm.
            }
        }
        throw new IllegalArgumentException("Unsupported MQTT client private key");
    }
}
