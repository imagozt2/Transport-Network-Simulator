package com.transport.simulator.repository;

import com.transport.simulator.entity.DeviceMqttCommand;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceMqttCommandRepository extends JpaRepository<DeviceMqttCommand, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select command from DeviceMqttCommand command join fetch command.device where command.id = :id")
    Optional<DeviceMqttCommand> findByIdForPublication(@Param("id") Long id);
}
