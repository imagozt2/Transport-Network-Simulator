package com.transport.simulator.repository;

import com.transport.simulator.entity.PassengerMobileDevice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassengerMobileDeviceRepository extends JpaRepository<PassengerMobileDevice, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from PassengerMobileDevice device join fetch device.passengerAccount where device.installationId = :installationId")
    Optional<PassengerMobileDevice> findByInstallationIdForUpdate(@Param("installationId") String installationId);

    List<PassengerMobileDevice> findAllByPassengerAccountIdOrderByLastSeenAtDesc(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from PassengerMobileDevice device where device.publicId = :publicId and device.passengerAccount.id = :accountId")
    Optional<PassengerMobileDevice> findOwnedByPublicIdForUpdate(@Param("publicId") String publicId,
            @Param("accountId") Long accountId);
}
