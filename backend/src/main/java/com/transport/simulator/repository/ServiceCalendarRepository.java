package com.transport.simulator.repository;

import com.transport.simulator.entity.ServiceCalendar;
import com.transport.simulator.enums.OperatingDayType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceCalendarRepository extends JpaRepository<ServiceCalendar, Long> {

    @Query("""
            SELECT serviceCalendar
            FROM ServiceCalendar serviceCalendar
            WHERE serviceCalendar.dayType = :dayType
              AND serviceCalendar.active = true
              AND serviceCalendar.validFrom <= :serviceDate
              AND (serviceCalendar.validUntil IS NULL OR serviceCalendar.validUntil >= :serviceDate)
            ORDER BY serviceCalendar.validFrom DESC, serviceCalendar.id DESC
            """)
    List<ServiceCalendar> findApplicableCalendars(
            @Param("dayType") OperatingDayType dayType,
            @Param("serviceDate") LocalDate serviceDate
    );
}
