package com.transport.simulator.repository;

import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.enums.LogOrigin;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceEventLogRepository extends JpaRepository<DeviceEventLog, Long> {

    Optional<DeviceEventLog> findByOriginAndExternalReference(
            LogOrigin origin,
            String externalReference
    );

    @Query("""
            SELECT deviceEvent
            FROM DeviceEventLog deviceEvent
            JOIN FETCH deviceEvent.device device
            JOIN FETCH deviceEvent.station
            WHERE device.active = true
              AND NOT EXISTS (
                SELECT newerEvent.id
                FROM DeviceEventLog newerEvent
                WHERE newerEvent.device = device
                  AND (
                      newerEvent.occurredAt > deviceEvent.occurredAt
                      OR (
                          newerEvent.occurredAt = deviceEvent.occurredAt
                          AND newerEvent.id > deviceEvent.id
                      )
                  )
            )
            """)
    List<DeviceEventLog> findLatestForEachDevice();
}
