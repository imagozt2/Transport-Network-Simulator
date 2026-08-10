package com.transport.simulator.mqtt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true")
public class MqttConnectionSupervisor {
    private final ControlCenterMqttClient mqttClient;

    public MqttConnectionSupervisor(ControlCenterMqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    @Scheduled(fixedDelayString = "${app.mqtt.reconnect-interval-ms:5000}")
    public void reconnectIfNecessary() {
        mqttClient.connectIfNecessary();
    }
}
