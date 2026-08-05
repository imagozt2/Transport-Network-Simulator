package com.transport.simulator.repository;

import com.transport.simulator.entity.IncidentComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentCommentRepository extends JpaRepository<IncidentComment, Long> {
    List<IncidentComment> findAllByIncidentIdOrderByCreatedAtAscIdAsc(Long incidentId);
}
