package com.transport.simulator.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mqtt")
public record MqttClientProperties(
        boolean enabled,
        String host,
        int port,
        boolean tls,
        String clientId,
        String username,
        String password,
        int connectionTimeoutSeconds,
        int keepAliveSeconds,
        String caCertificate,
        String clientCertificate,
        String clientPrivateKey
) {
    public String serverUri() {
        return (tls ? "ssl" : "tcp") + "://" + host + ":" + port;
    }

    public void validate() {
        if (!enabled) return;
        requireText(host, "MQTT host");
        requireText(clientId, "MQTT client id");
        requireRange(port, 1, 65_535, "MQTT port");
        requireRange(connectionTimeoutSeconds, 1, 300, "MQTT connection timeout");
        requireRange(keepAliveSeconds, 1, 65_535, "MQTT keep alive");
        if (hasText(username) != hasText(password)) {
            throw new IllegalStateException("MQTT username and password must be configured together");
        }
        if (tls && (!hasText(caCertificate) || !hasText(clientCertificate)
                || !hasText(clientPrivateKey))) {
            throw new IllegalStateException("MQTT TLS requires CA, client certificate and private key");
        }
    }

    private static void requireText(String value, String name) {
        if (!hasText(value)) throw new IllegalStateException(name + " is required");
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) throw new IllegalStateException(name + " is invalid");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
