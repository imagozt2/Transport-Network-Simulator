package com.transport.simulator.repository;

import com.transport.simulator.entity.Device;
import com.transport.simulator.enums.DeviceMqttPresence;
import com.transport.simulator.repository.projection.DeviceStatusCountProjection;
import com.transport.simulator.repository.projection.DeviceTypeCountProjection;
import com.transport.simulator.repository.projection.StationDeviceSummaryProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    long countByActiveTrue();

    Optional<Device> findByCodeAndActiveTrue(String code);

    List<Device> findAllByMqttPresenceAndActiveTrue(DeviceMqttPresence mqttPresence);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from Device device join fetch device.station where device.id = :id and device.active = true")
    Optional<Device> findByIdForMqttUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "station")
    List<Device> findAllByActiveTrueOrderByCodeAsc();

    @Query("""
            SELECT device.status AS status, COUNT(device.id) AS total
            FROM Device device
            WHERE device.active = true
            GROUP BY device.status
            """)
    List<DeviceStatusCountProjection> countActiveDevicesByStatus();

    @Query("""
            SELECT device.type AS type, COUNT(device.id) AS total
            FROM Device device
            WHERE device.active = true
            GROUP BY device.type
            """)
    List<DeviceTypeCountProjection> countActiveDevicesByType();

    @Query("""
            SELECT device.station.id AS stationId,
                   device.type AS type,
                   device.status AS status,
                   COUNT(device.id) AS total
            FROM Device device
            WHERE device.active = true
            GROUP BY device.station.id, device.type, device.status
            """)
    List<StationDeviceSummaryProjection> summarizeActiveDevicesByStation();
}
