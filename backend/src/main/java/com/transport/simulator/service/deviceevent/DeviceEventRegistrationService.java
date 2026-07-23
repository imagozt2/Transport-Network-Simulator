package com.transport.simulator.service.deviceevent;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.OperationalLog;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.OperationalLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceEventRegistrationService {

    private final DeviceRepository deviceRepository;
    private final OperationalLogRepository operationalLogRepository;
    private final DeviceStatusTransitionPolicy statusTransitionPolicy;

    public DeviceEventRegistrationService(
            DeviceRepository deviceRepository,
            OperationalLogRepository operationalLogRepository,
            DeviceStatusTransitionPolicy statusTransitionPolicy
    ) {
        this.deviceRepository = deviceRepository;
        this.operationalLogRepository = operationalLogRepository;
        this.statusTransitionPolicy = statusTransitionPolicy;
    }

    @Transactional
    public OperationalLog register(DeviceEvent event) {
        Device device = deviceRepository.findByCodeAndActiveTrue(event.deviceCode())
                .orElseThrow(() -> new UnknownDeviceException(event.deviceCode()));

        device.recordEvent(
                statusTransitionPolicy.resolve(device.getStatus(), event),
                event.occurredAt()
        );

        OperationalLog log = new OperationalLog(
                event.origin(),
                event.type(),
                event.severity(),
                event.message(),
                device,
                event.occurredAt(),
                event.externalReference(),
                event.payloadJson()
        );

        return operationalLogRepository.save(log);
    }
}
