package com.transport.simulator.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    Clock serviceClock(@Value("${app.operation.zone-id:Europe/Madrid}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
