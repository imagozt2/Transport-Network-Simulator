package com.transport.simulator.service.deviceevent;

import com.transport.simulator.entity.Device;
import com.transport.simulator.enums.DeviceMqttPresence;
import com.transport.simulator.enums.DeviceOperationalState;
import com.transport.simulator.mqtt.AuthenticatedMqttMachine;
import com.transport.simulator.repository.DeviceRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MqttDeviceStateService {
    private final DeviceRepository deviceRepository;

    public MqttDeviceStateService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public boolean updatePresence(AuthenticatedMqttMachine machine,
            DeviceMqttPresence presence, LocalDateTime changedAt) {
        Device device = ownedDevice(machine);
        return device.recordMqttPresence(presence, changedAt);
    }

    @Transactional
    public boolean updateOperationalState(AuthenticatedMqttMachine machine,
            DeviceOperationalState state, String serviceMode, String softwareVersion,
            long uptimeSeconds, LocalDateTime occurredAt) {
        Device device = ownedDevice(machine);
        return device.recordMqttStatus(state, serviceMode, softwareVersion,
                uptimeSeconds, occurredAt);
    }

    private Device ownedDevice(AuthenticatedMqttMachine machine) {
        Device device = deviceRepository.findByIdForMqttUpdate(machine.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated MQTT machine is inactive"));
        if (!device.getCode().equals(machine.deviceCode())
                || !device.getStation().getCode().equals(machine.stationCode())
                || device.getType() != machine.deviceType()) {
            throw new IllegalArgumentException("Authenticated MQTT machine no longer matches inventory");
        }
        return device;
    }
}
