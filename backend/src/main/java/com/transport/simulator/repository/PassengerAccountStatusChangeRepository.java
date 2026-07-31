package com.transport.simulator.repository;

import com.transport.simulator.entity.PassengerAccountStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassengerAccountStatusChangeRepository
        extends JpaRepository<PassengerAccountStatusChange, Long> {

    void deleteAllByPassengerAccountId(Long passengerAccountId);
}
