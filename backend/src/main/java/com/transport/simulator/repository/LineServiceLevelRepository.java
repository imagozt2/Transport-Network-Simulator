package com.transport.simulator.repository;

import com.transport.simulator.entity.LineServiceLevel;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineServiceLevelRepository extends JpaRepository<LineServiceLevel, Long> {

    @EntityGraph(attributePaths = {"servicePeriod", "servicePeriod.serviceCalendar"})
    List<LineServiceLevel> findAllByLineIdAndServicePeriodServiceCalendarIdAndActiveTrueOrderByServicePeriodPeriodOrderAsc(
            Long lineId,
            Long serviceCalendarId
    );
}
