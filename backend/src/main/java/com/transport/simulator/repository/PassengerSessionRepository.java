package com.transport.simulator.repository;

import com.transport.simulator.entity.PassengerSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface PassengerSessionRepository extends JpaRepository<PassengerSession, Long> {

    @Query("select session from PassengerSession session join fetch session.mobileDevice device join fetch device.passengerAccount where session.accessTokenHash = :hash")
    Optional<PassengerSession> findByAccessTokenHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from PassengerSession session join fetch session.mobileDevice device join fetch device.passengerAccount where session.refreshTokenHash = :hash")
    Optional<PassengerSession> findByRefreshTokenHashForUpdate(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from PassengerSession session join fetch session.mobileDevice device join fetch device.passengerAccount where session.id = :id")
    Optional<PassengerSession> findByIdForUpdate(@Param("id") Long id);

    List<PassengerSession> findAllByMobileDevicePassengerAccountIdAndRevokedAtIsNullOrderByLastUsedAtDesc(
            Long accountId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session from PassengerSession session
            join fetch session.mobileDevice device
            join fetch device.passengerAccount
            where session.publicId = :publicId and device.passengerAccount.id = :accountId
            """)
    Optional<PassengerSession> findOwnedByPublicIdForUpdate(
            @Param("publicId") String publicId,
            @Param("accountId") Long accountId
    );

    @Modifying
    @Query("""
            update PassengerSession session
            set session.revokedAt = :revokedAt, session.revocationReason = :reason
            where session.mobileDevice.passengerAccount.id = :accountId and session.revokedAt is null
            """)
    int revokeAllActiveByAccountId(
            @Param("accountId") Long accountId,
            @Param("revokedAt") java.time.LocalDateTime revokedAt,
            @Param("reason") String reason
    );

    @Modifying
    @Query("update PassengerSession session set session.revokedAt = :revokedAt, session.revocationReason = :reason where session.mobileDevice.id = :deviceId and session.revokedAt is null")
    int revokeAllActiveByMobileDeviceId(@Param("deviceId") Long deviceId,
            @Param("revokedAt") java.time.LocalDateTime revokedAt, @Param("reason") String reason);
}
