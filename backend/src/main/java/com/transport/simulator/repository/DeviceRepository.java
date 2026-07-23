package com.transport.simulator.repository;

import com.transport.simulator.entity.Device;
import com.transport.simulator.repository.projection.DeviceStatusCountProjection;
import com.transport.simulator.repository.projection.DeviceTypeCountProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    long countByActiveTrue();

    Optional<Device> findByCodeAndActiveTrue(String code);

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
}
