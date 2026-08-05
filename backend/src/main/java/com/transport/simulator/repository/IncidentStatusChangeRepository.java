package com.transport.simulator.repository;

import com.transport.simulator.entity.IncidentStatusChange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentStatusChangeRepository extends JpaRepository<IncidentStatusChange, Long> {
    List<IncidentStatusChange> findAllByIncidentIdOrderByCreatedAtAscIdAsc(Long incidentId);
}
