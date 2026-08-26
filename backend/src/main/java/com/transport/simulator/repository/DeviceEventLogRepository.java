package com.transport.simulator.repository;

import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(
            value = """
                    SELECT eventLog
                    FROM DeviceEventLog eventLog
                    LEFT JOIN FETCH eventLog.device device
                    LEFT JOIN FETCH eventLog.station station
                    WHERE (:origin IS NULL OR eventLog.origin = :origin)
                      AND (:severity IS NULL OR eventLog.severity = :severity)
                      AND (:eventType IS NULL OR eventLog.eventType = :eventType)
                      AND (:deviceType IS NULL OR device.type = :deviceType)
                      AND (:deviceCode IS NULL OR LOWER(device.code) = LOWER(:deviceCode))
                      AND (:stationCode IS NULL OR LOWER(station.code) = LOWER(:stationCode))
                      AND (:occurredFrom IS NULL OR eventLog.occurredAt >= :occurredFrom)
                      AND (:occurredTo IS NULL OR eventLog.occurredAt <= :occurredTo)
                    """,
            countQuery = """
                    SELECT COUNT(eventLog)
                    FROM DeviceEventLog eventLog
                    LEFT JOIN eventLog.device device
                    LEFT JOIN eventLog.station station
                    WHERE (:origin IS NULL OR eventLog.origin = :origin)
                      AND (:severity IS NULL OR eventLog.severity = :severity)
                      AND (:eventType IS NULL OR eventLog.eventType = :eventType)
                      AND (:deviceType IS NULL OR device.type = :deviceType)
                      AND (:deviceCode IS NULL OR LOWER(device.code) = LOWER(:deviceCode))
                      AND (:stationCode IS NULL OR LOWER(station.code) = LOWER(:stationCode))
                      AND (:occurredFrom IS NULL OR eventLog.occurredAt >= :occurredFrom)
                      AND (:occurredTo IS NULL OR eventLog.occurredAt <= :occurredTo)
                    """
    )
    Page<DeviceEventLog> findFiltered(
            @Param("origin") LogOrigin origin,
            @Param("severity") LogSeverity severity,
            @Param("eventType") DeviceEventType eventType,
            @Param("deviceType") DeviceType deviceType,
            @Param("deviceCode") String deviceCode,
            @Param("stationCode") String stationCode,
            @Param("occurredFrom") LocalDateTime occurredFrom,
            @Param("occurredTo") LocalDateTime occurredTo,
            Pageable pageable
    );
}
