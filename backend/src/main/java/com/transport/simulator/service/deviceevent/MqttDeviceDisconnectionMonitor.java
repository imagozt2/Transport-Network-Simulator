package com.transport.simulator.service.deviceevent;

import com.transport.simulator.entity.Device;
import com.transport.simulator.enums.DeviceMqttPresence;
import com.transport.simulator.repository.DeviceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true")
public class MqttDeviceDisconnectionMonitor {
    private final DeviceRepository deviceRepository;
    private final Clock clock;
    private final Duration staleAfter;

    public MqttDeviceDisconnectionMonitor(DeviceRepository deviceRepository, Clock clock,
            @Value("${app.mqtt.device-stale-after:90s}") Duration staleAfter) {
        this.deviceRepository = deviceRepository;
        this.clock = clock;
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${app.mqtt.device-monitor-interval-ms:10000}")
    @Transactional
    public void markDisconnectedDevices() {
        LocalDateTime staleBefore = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .minus(staleAfter);
        for (Device device : deviceRepository.findAllByMqttPresenceAndActiveTrue(DeviceMqttPresence.ONLINE)) {
            device.markDisconnectedWhenStale(staleBefore);
        }
    }
}
