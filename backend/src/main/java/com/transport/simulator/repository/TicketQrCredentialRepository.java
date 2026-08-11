package com.transport.simulator.repository;

import com.transport.simulator.entity.TicketQrCredential;
import com.transport.simulator.enums.TicketQrCredentialStatus;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketQrCredentialRepository extends JpaRepository<TicketQrCredential, Long> {

    Optional<TicketQrCredential> findFirstByTicketIdAndStatusOrderByIssuedAtDesc(
            Long ticketId,
            TicketQrCredentialStatus status
    );

    @Query("""
            select credential
            from TicketQrCredential credential
            join fetch credential.ticket
            join fetch credential.support support
            join fetch support.ticket
            where credential.credentialId = :credentialId
            """)
    Optional<TicketQrCredential> findForVerificationByCredentialId(
            @Param("credentialId") UUID credentialId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from TicketQrCredential credential where credential.id = :id")
    Optional<TicketQrCredential> lockById(@Param("id") Long id);
}
