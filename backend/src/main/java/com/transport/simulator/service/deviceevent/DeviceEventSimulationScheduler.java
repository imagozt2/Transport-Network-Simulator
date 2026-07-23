package com.transport.simulator.service.deviceevent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.device-event-simulation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
class DeviceEventSimulationScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DeviceEventSimulationScheduler.class);

    private final DeviceEventSimulationService simulationService;
    private final int eventsPerCycle;

    public DeviceEventSimulationScheduler(
            DeviceEventSimulationService simulationService,
            @Value("${app.device-event-simulation.events-per-cycle:5}") int eventsPerCycle
    ) {
        this.simulationService = simulationService;
        this.eventsPerCycle = eventsPerCycle;
    }

    @Scheduled(
            fixedDelayString = "${app.device-event-simulation.interval-ms:60000}",
            initialDelayString = "${app.device-event-simulation.initial-delay-ms:10000}"
    )
    public void generateDeviceEvents() {
        try {
            int generatedEvents = simulationService.runCycle(eventsPerCycle);
            LOGGER.debug(
                    "Automatic device event simulation cycle completed: {} events generated",
                    generatedEvents
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Automatic device event simulation cycle failed", exception);
        }
    }
}
