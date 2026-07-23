package com.transport.simulator.service.deviceevent;

import com.transport.simulator.entity.Device;
import com.transport.simulator.repository.DeviceRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class DeviceEventSimulationService {

    private static final int MAX_EVENTS_PER_CYCLE = 100;

    private final DeviceRepository deviceRepository;
    private final SimulatedDeviceEventGenerator eventGenerator;
    private final DeviceEventRegistrationService eventRegistrationService;

    public DeviceEventSimulationService(
            DeviceRepository deviceRepository,
            SimulatedDeviceEventGenerator eventGenerator,
            DeviceEventRegistrationService eventRegistrationService
    ) {
        this.deviceRepository = deviceRepository;
        this.eventGenerator = eventGenerator;
        this.eventRegistrationService = eventRegistrationService;
    }

    private List<DeviceEvent> generateEvents(int requestedEventCount) {
        int eventCount = Math.clamp(requestedEventCount, 1, MAX_EVENTS_PER_CYCLE);
        List<Device> activeDevices = new ArrayList<>(
                deviceRepository.findAllByActiveTrueOrderByCodeAsc()
        );

        if (activeDevices.isEmpty()) {
            return List.of();
        }

        Collections.shuffle(activeDevices);

        return activeDevices.stream()
                .limit(Math.min(eventCount, activeDevices.size()))
                .map(eventGenerator::generate)
                .toList();
    }

    public int runCycle(int requestedEventCount) {
        List<DeviceEvent> events = generateEvents(requestedEventCount);
        events.forEach(eventRegistrationService::register);
        return events.size();
    }
}
