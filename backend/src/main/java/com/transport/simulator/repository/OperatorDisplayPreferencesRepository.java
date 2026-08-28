package com.transport.simulator.repository;

import com.transport.simulator.entity.OperatorDisplayPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorDisplayPreferencesRepository
        extends JpaRepository<OperatorDisplayPreferences, Long> {
}
