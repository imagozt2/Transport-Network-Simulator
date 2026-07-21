package com.transport.simulator.repository;

import com.transport.simulator.entity.ServicePeriod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServicePeriodRepository extends JpaRepository<ServicePeriod, Long> {

    List<ServicePeriod> findAllByServiceCalendarIdAndActiveTrueOrderByPeriodOrderAsc(Long calendarId);
}
