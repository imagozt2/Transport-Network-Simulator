package com.transport.simulator.repository;

import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.enums.PassengerAccountStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassengerAccountRepository extends JpaRepository<PassengerAccount, Long> {

    Optional<PassengerAccount> findByPublicId(String publicId);

    long countByStatus(PassengerAccountStatus status);

    long countByEmailVerifiedAtIsNull();

    @Query("""
            SELECT passenger
            FROM PassengerAccount passenger
            WHERE (
                :search IS NULL
                OR LOWER(passenger.publicId) LIKE :search
                OR LOWER(passenger.email) LIKE :search
                OR LOWER(passenger.firstName) LIKE :search
                OR LOWER(passenger.lastName) LIKE :search
                OR LOWER(CONCAT(passenger.firstName, ' ', passenger.lastName)) LIKE :search
            )
            AND (:status IS NULL OR passenger.status = :status)
            AND (
                :emailVerified IS NULL
                OR (:emailVerified = TRUE AND passenger.emailVerifiedAt IS NOT NULL)
                OR (:emailVerified = FALSE AND passenger.emailVerifiedAt IS NULL)
            )
            """)
    Page<PassengerAccount> findAdministrativePage(
            @Param("search") String search,
            @Param("status") PassengerAccountStatus status,
            @Param("emailVerified") Boolean emailVerified,
            Pageable pageable
    );
}
