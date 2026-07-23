package com.transport.simulator.repository;

import com.transport.simulator.entity.OperationalLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationalLogRepository extends JpaRepository<OperationalLog, Long> {
}
