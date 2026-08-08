package com.transport.simulator.repository;

import com.transport.simulator.entity.PassengerSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassengerSessionRepository extends JpaRepository<PassengerSession, Long> {

    @Query("select session from PassengerSession session join fetch session.passengerAccount where session.accessTokenHash = :hash")
    Optional<PassengerSession> findByAccessTokenHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from PassengerSession session join fetch session.passengerAccount where session.refreshTokenHash = :hash")
    Optional<PassengerSession> findByRefreshTokenHashForUpdate(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from PassengerSession session join fetch session.passengerAccount where session.id = :id")
    Optional<PassengerSession> findByIdForUpdate(@Param("id") Long id);
}
