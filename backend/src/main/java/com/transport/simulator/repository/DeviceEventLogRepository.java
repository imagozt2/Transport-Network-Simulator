package com.transport.simulator.repository;

import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.enums.LogOrigin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceEventLogRepository extends JpaRepository<DeviceEventLog, Long> {

    Optional<DeviceEventLog> findByOriginAndExternalReference(
            LogOrigin origin,
            String externalReference
    );
}
