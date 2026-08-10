package com.transport.simulator.service.deviceevent;

import com.transport.simulator.entity.Device;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.service.ServiceOperationStateService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class DeviceEventSimulationService {

    private final DeviceRepository deviceRepository;
    private final SimulatedDeviceEventGenerator eventGenerator;
    private final DeviceEventRegistrationService eventRegistrationService;
    private final ServiceOperationStateService serviceOperationStateService;

    public DeviceEventSimulationService(
            DeviceRepository deviceRepository,
            SimulatedDeviceEventGenerator eventGenerator,
            DeviceEventRegistrationService eventRegistrationService,
            ServiceOperationStateService serviceOperationStateService
    ) {
        this.deviceRepository = deviceRepository;
        this.eventGenerator = eventGenerator;
        this.eventRegistrationService = eventRegistrationService;
        this.serviceOperationStateService = serviceOperationStateService;
    }

    private DeviceEvent generateOperationalEvent(List<Device> activeDevices) {
        List<Device> shuffledDevices = new ArrayList<>(activeDevices);
        Collections.shuffle(shuffledDevices);
        return eventGenerator.generateOperationalActivity(shuffledDevices.getFirst());
    }

    public int runCycle() {
        List<Device> simulatedDevices = deviceRepository.findAllByActiveTrueOrderByCodeAsc()
                .stream()
                .filter(device -> !device.isMqttManaged())
                .toList();
        if (simulatedDevices.isEmpty()) {
            return 0;
        }

        boolean serviceOpen = serviceOperationStateService.getCurrentState().serviceOpen();
        DeviceStatus expectedStatus = serviceOpen ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE;
        List<DeviceEvent> events = new ArrayList<>();

        simulatedDevices.stream()
                .filter(device -> device.getStatus() != expectedStatus)
                .map(device -> eventGenerator.generateServiceState(device, serviceOpen))
                .forEach(events::add);

        if (serviceOpen) {
            events.add(generateOperationalEvent(simulatedDevices));
        }

        events.forEach(eventRegistrationService::register);
        return events.size();
    }
}
