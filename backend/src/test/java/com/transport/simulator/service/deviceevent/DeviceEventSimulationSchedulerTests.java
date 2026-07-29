package com.transport.simulator.service.deviceevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class DeviceEventSimulationSchedulerTests {

    @Test
    void shouldScheduleOneSimulationCycleEverySecond() throws NoSuchMethodException {
        Method scheduledMethod = DeviceEventSimulationScheduler.class
                .getDeclaredMethod("generateDeviceEvents");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedRateString())
                .isEqualTo("${app.device-event-simulation.interval-ms:1000}");
        assertThat(scheduled.initialDelayString())
                .isEqualTo("${app.device-event-simulation.initial-delay-ms:1000}");
    }

    @Test
    void shouldDelegateEachScheduledExecutionToOneSimulationCycle() {
        DeviceEventSimulationService simulationService =
                org.mockito.Mockito.mock(DeviceEventSimulationService.class);
        when(simulationService.runCycle()).thenReturn(1);
        DeviceEventSimulationScheduler scheduler =
                new DeviceEventSimulationScheduler(simulationService);

        scheduler.generateDeviceEvents();

        verify(simulationService).runCycle();
    }
}
