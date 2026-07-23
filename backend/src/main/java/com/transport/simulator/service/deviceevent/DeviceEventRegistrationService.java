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

    public DeviceEventRegistrationService(
            DeviceRepository deviceRepository,
            OperationalLogRepository operationalLogRepository
    ) {
        this.deviceRepository = deviceRepository;
        this.operationalLogRepository = operationalLogRepository;
    }

    @Transactional
    public OperationalLog register(DeviceEvent event) {
        Device device = deviceRepository.findByCodeAndActiveTrue(event.deviceCode())
                .orElseThrow(() -> new UnknownDeviceException(event.deviceCode()));

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
