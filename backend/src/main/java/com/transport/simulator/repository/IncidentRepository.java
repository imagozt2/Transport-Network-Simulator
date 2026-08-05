package com.transport.simulator.repository;

import com.transport.simulator.entity.Incident;
import com.transport.simulator.enums.IncidentCategory;
import com.transport.simulator.enums.IncidentPriority;
import com.transport.simulator.enums.IncidentStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByCode(String code);

    long countByStatus(IncidentStatus status);

    @Query("""
            SELECT incident
            FROM Incident incident
            LEFT JOIN incident.assignedTo assignedOperator
            WHERE (
                :search IS NULL
                OR LOWER(incident.code) LIKE :search
                OR LOWER(incident.title) LIKE :search
                OR LOWER(incident.description) LIKE :search
            )
            AND (:status IS NULL OR incident.status = :status)
            AND (:priority IS NULL OR incident.priority = :priority)
            AND (:category IS NULL OR incident.category = :category)
            AND (:assignedOperatorId IS NULL OR assignedOperator.id = :assignedOperatorId)
            """)
    Page<Incident> findManagementPage(
            @Param("search") String search,
            @Param("status") IncidentStatus status,
            @Param("priority") IncidentPriority priority,
            @Param("category") IncidentCategory category,
            @Param("assignedOperatorId") Long assignedOperatorId,
            Pageable pageable
    );
}
