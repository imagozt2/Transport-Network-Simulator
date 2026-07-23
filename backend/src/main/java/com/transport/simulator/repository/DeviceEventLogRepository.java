package com.transport.simulator.repository;

import com.transport.simulator.entity.DeviceEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceEventLogRepository extends JpaRepository<DeviceEventLog, Long> {
}
