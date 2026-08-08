package com.transport.simulator.repository;

import com.transport.simulator.entity.TicketQrCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketQrCredentialRepository extends JpaRepository<TicketQrCredential, Long> {

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
}
