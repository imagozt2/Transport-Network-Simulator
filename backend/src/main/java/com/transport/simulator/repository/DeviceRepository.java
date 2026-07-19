package com.transport.simulator.repository;

import com.transport.simulator.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    long countByActiveTrue();
}
