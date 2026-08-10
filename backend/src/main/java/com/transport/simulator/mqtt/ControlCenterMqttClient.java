package com.transport.simulator.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public class ControlCenterMqttClient implements SmartLifecycle, MqttCallbackExtended {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControlCenterMqttClient.class);

    private final MqttClientProperties properties;
    private final MqttAsyncClient client;
    private final MqttConnectOptions connectOptions;
    private final Clock clock;
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final AtomicReference<MqttConnectionSnapshot> snapshot;
    private volatile boolean running;

    public ControlCenterMqttClient(MqttClientProperties properties, Clock clock) throws MqttException {
        this.properties = properties;
        this.clock = clock;
        properties.validate();
        this.client = new MqttAsyncClient(properties.serverUri(), properties.clientId(),
                new MemoryPersistence());
        this.client.setCallback(this);
        this.connectOptions = options(properties);
        this.snapshot = new AtomicReference<>(new MqttConnectionSnapshot(
                properties.enabled() ? MqttConnectionState.DISCONNECTED : MqttConnectionState.DISABLED,
                properties.clientId(), properties.serverUri(), now(), null));
    }

    @Override
    public void start() {
        running = true;
        if (!properties.enabled() || client.isConnected()) return;
        update(MqttConnectionState.CONNECTING, null);
        try {
            client.connect(connectOptions, null, new IMqttActionListener() {
                @Override public void onSuccess(IMqttToken token) {
                    update(MqttConnectionState.CONNECTED, null);
                }
                @Override public void onFailure(IMqttToken token, Throwable error) {
                    update(MqttConnectionState.DISCONNECTED, message(error));
                    LOGGER.warn("MQTT initial connection to {} failed: {}",
                            properties.serverUri(), message(error));
                }
            });
        } catch (MqttException exception) {
            update(MqttConnectionState.DISCONNECTED, exception.getMessage());
            LOGGER.warn("MQTT connection could not be started", exception);
        }
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (client.isConnected()) client.disconnect().waitForCompletion(5_000);
            client.close();
        } catch (MqttException exception) {
            LOGGER.warn("MQTT client could not close cleanly", exception);
        } finally {
            update(MqttConnectionState.STOPPED, null);
        }
    }

    public void publish(String topic, byte[] payload, int qos, boolean retained) {
        requireConnected();
        try {
            client.publish(topic, payload, qos, retained);
        } catch (MqttException exception) {
            throw new MqttTransportException("MQTT message could not be published", exception);
        }
    }

    public void publish(String topic, String payload, int qos, boolean retained) {
        publish(topic, payload.getBytes(StandardCharsets.UTF_8), qos, retained);
    }

    public void subscribe(String topicFilter, int qos, BiConsumer<String, byte[]> consumer) {
        subscriptions.put(topicFilter, new Subscription(qos, consumer));
        if (!client.isConnected()) return;
        subscribeNow(topicFilter, qos);
    }

    public MqttConnectionSnapshot connection() { return snapshot.get(); }
    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        update(MqttConnectionState.CONNECTED, null);
        restoreSubscriptions();
        LOGGER.info("MQTT client {} connected to {}{}", properties.clientId(), serverUri,
                reconnect ? " after reconnection" : "");
    }

    @Override
    public void connectionLost(Throwable cause) {
        update(MqttConnectionState.DISCONNECTED, message(cause));
        LOGGER.warn("MQTT connection lost: {}", message(cause));
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        subscriptions.entrySet().stream()
                .filter(entry -> matches(entry.getKey(), topic))
                .forEach(entry -> entry.getValue().consumer().accept(
                        topic, message.getPayload().clone()));
    }

    @Override public void deliveryComplete(IMqttDeliveryToken token) {}

    private void restoreSubscriptions() {
        subscriptions.forEach((topic, subscription) -> subscribeNow(topic, subscription.qos()));
    }

    private void subscribeNow(String topicFilter, int qos) {
        try {
            client.subscribe(topicFilter, qos);
        } catch (MqttException exception) {
            throw new MqttTransportException("MQTT subscription could not be created", exception);
        }
    }

    private MqttConnectOptions options(MqttClientProperties value) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setConnectionTimeout(value.connectionTimeoutSeconds());
        options.setKeepAliveInterval(value.keepAliveSeconds());
        if (value.username() != null && !value.username().isBlank()) {
            options.setUserName(value.username());
            options.setPassword(value.password().toCharArray());
        }
        if (value.tls()) options.setSocketFactory(MqttTlsSocketFactory.create(value));
        return options;
    }

    private void requireConnected() {
        if (!client.isConnected()) throw new MqttTransportException("MQTT client is not connected");
    }

    private boolean matches(String filter, String topic) {
        String[] expected = filter.split("/", -1);
        String[] actual = topic.split("/", -1);
        if (expected.length != actual.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (!expected[index].equals("+") && !expected[index].equals(actual[index])) return false;
        }
        return true;
    }

    private void update(MqttConnectionState state, String error) {
        snapshot.set(new MqttConnectionSnapshot(state, properties.clientId(),
                properties.serverUri(), now(), error));
    }

    private Instant now() { return clock.instant(); }
    private String message(Throwable error) {
        return error == null || error.getMessage() == null ? "Unknown MQTT error" : error.getMessage();
    }

    private record Subscription(int qos, BiConsumer<String, byte[]> consumer) {
        private Subscription {
            if (qos < 0 || qos > 2) throw new IllegalArgumentException("MQTT QoS must be between 0 and 2");
            if (consumer == null) throw new IllegalArgumentException("MQTT consumer is required");
        }
    }
}
