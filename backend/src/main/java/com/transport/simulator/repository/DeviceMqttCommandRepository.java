package com.transport.simulator.repository;

import com.transport.simulator.entity.DeviceMqttCommand;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import com.transport.simulator.enums.DeviceMqttCommandStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceMqttCommandRepository extends JpaRepository<DeviceMqttCommand, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select command from DeviceMqttCommand command join fetch command.device where command.id = :id")
    Optional<DeviceMqttCommand> findByIdForPublication(@Param("id") Long id);

    @Query("""
            select command.id from DeviceMqttCommand command
            where command.status in :statuses
              and command.expiresAt > :now
              and command.publicationAttempts < :maxAttempts
            order by command.requestedAt asc
            """)
    List<Long> findRecoverableCommandIds(
            @Param("statuses") List<DeviceMqttCommandStatus> statuses,
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable);
}
