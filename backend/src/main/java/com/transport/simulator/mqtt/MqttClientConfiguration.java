package com.transport.simulator.mqtt;

import java.time.Clock;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MqttClientProperties.class)
public class MqttClientConfiguration {
    @Bean
    ControlCenterMqttClient controlCenterMqttClient(
            MqttClientProperties properties,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) throws MqttException {
        return new ControlCenterMqttClient(properties, clock, eventPublisher);
    }
}
