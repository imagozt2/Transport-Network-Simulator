package com.transport.simulator.repository;

import com.transport.simulator.entity.DeviceMqttIdentity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceMqttIdentityRepository extends JpaRepository<DeviceMqttIdentity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select identity from DeviceMqttIdentity identity join fetch identity.device device join fetch device.station where identity.mqttClientId = :clientId")
    Optional<DeviceMqttIdentity> findByClientIdForAuthentication(@Param("clientId") String clientId);
}
