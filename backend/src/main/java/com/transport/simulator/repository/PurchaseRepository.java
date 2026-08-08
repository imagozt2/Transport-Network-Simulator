package com.transport.simulator.repository;

import com.transport.simulator.entity.Purchase;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByExternalReference(String externalReference);

    Optional<Purchase> findByCodeAndPassengerAccountId(String code, Long passengerAccountId);
}
