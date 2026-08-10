package com.transport.simulator.mqtt;

import com.transport.simulator.enums.DeviceMqttCommandStatus;
import com.transport.simulator.repository.DeviceMqttCommandRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true")
public class MqttPendingCommandRecovery {
    private static final List<DeviceMqttCommandStatus> RECOVERABLE_STATUSES = List.of(
            DeviceMqttCommandStatus.PENDING, DeviceMqttCommandStatus.PUBLISH_FAILED);

    private final DeviceMqttCommandRepository commandRepository;
    private final MqttDeviceCommandPublisher commandPublisher;
    private final ControlCenterMqttClient mqttClient;
    private final Clock clock;
    private final int maxAttempts;
    private final int batchSize;

    public MqttPendingCommandRecovery(DeviceMqttCommandRepository commandRepository,
            MqttDeviceCommandPublisher commandPublisher, ControlCenterMqttClient mqttClient,
            Clock clock,
            @Value("${app.mqtt.pending-command-max-attempts:10}") int maxAttempts,
            @Value("${app.mqtt.pending-command-batch-size:100}") int batchSize) {
        this.commandRepository = commandRepository;
        this.commandPublisher = commandPublisher;
        this.mqttClient = mqttClient;
        this.clock = clock;
        if (maxAttempts < 1) throw new IllegalArgumentException("MQTT command max attempts must be positive");
        if (batchSize < 1) throw new IllegalArgumentException("MQTT command batch size must be positive");
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    @EventListener
    public void onConnected(MqttConnectedEvent ignored) {
        recoverPendingCommands();
    }

    @Scheduled(fixedDelayString = "${app.mqtt.pending-command-retry-interval-ms:10000}")
    public void retryPendingCommands() {
        recoverPendingCommands();
    }

    @Transactional(readOnly = true)
    public void recoverPendingCommands() {
        if (mqttClient.connection().state() != MqttConnectionState.CONNECTED) return;
        List<Long> commandIds = commandRepository.findRecoverableCommandIds(
                RECOVERABLE_STATUSES, LocalDateTime.now(clock), maxAttempts,
                PageRequest.of(0, batchSize));
        commandIds.forEach(commandPublisher::republish);
    }
}
