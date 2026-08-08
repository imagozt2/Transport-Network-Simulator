package com.transport.simulator.repository;

import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.PassengerAccountToken;
import com.transport.simulator.enums.PassengerAccountTokenType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassengerAccountTokenRepository extends JpaRepository<PassengerAccountToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token from PassengerAccountToken token
            join fetch token.passengerAccount
            where token.tokenHash = :hash and token.type = :type
            """)
    Optional<PassengerAccountToken> findForUse(
            @Param("hash") String hash,
            @Param("type") PassengerAccountTokenType type
    );

    @Modifying
    @Query("delete from PassengerAccountToken token where token.passengerAccount = :account and token.type = :type and token.usedAt is null")
    void deleteUnusedByAccountAndType(
            @Param("account") PassengerAccount account,
            @Param("type") PassengerAccountTokenType type
    );
}
