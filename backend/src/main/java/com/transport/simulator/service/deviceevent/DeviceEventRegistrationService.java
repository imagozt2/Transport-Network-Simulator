package com.transport.simulator.service.deviceevent;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.enums.DeviceEventSource;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.repository.DeviceEventLogRepository;
import com.transport.simulator.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeviceEventRegistrationService {

    private final DeviceRepository deviceRepository;
    private final DeviceEventLogRepository deviceEventLogRepository;
    private final DeviceStatusTransitionPolicy statusTransitionPolicy;

    public DeviceEventRegistrationService(
            DeviceRepository deviceRepository,
            DeviceEventLogRepository deviceEventLogRepository,
            DeviceStatusTransitionPolicy statusTransitionPolicy
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceEventLogRepository = deviceEventLogRepository;
        this.statusTransitionPolicy = statusTransitionPolicy;
    }

    @Transactional
    public DeviceEventLog register(DeviceEvent event) {
        Device device = deviceRepository.findByCodeAndActiveTrue(event.deviceCode())
                .orElseThrow(() -> new UnknownDeviceException(event.deviceCode()));

        DeviceStatus previousStatus = device.getStatus();
        if (event.source() != DeviceEventSource.REAL
                || device.recordMqttCommunication(event.occurredAt())) {
            device.recordEvent(
                    statusTransitionPolicy.resolve(previousStatus, event),
                    event.occurredAt()
            );
        }

        DeviceEventLog log = new DeviceEventLog(
                event.origin(),
                event.source(),
                event.type(),
                event.severity(),
                event.message(),
                device,
                event.occurredAt(),
                event.externalReference(),
                event.payloadJson()
        );

        return deviceEventLogRepository.save(log);
    }
}
