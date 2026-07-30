package com.transport.simulator.repository;

import com.transport.simulator.entity.OperatorAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorAccountRepository extends JpaRepository<OperatorAccount, Long> {

    Optional<OperatorAccount> findByUsernameIgnoreCaseOrEmailIgnoreCase(
            String username,
            String email
    );
}
