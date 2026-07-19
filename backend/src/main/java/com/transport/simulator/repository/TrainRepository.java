package com.transport.simulator.repository;

import com.transport.simulator.entity.Train;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainRepository extends JpaRepository<Train, Long> {

    long countByActiveTrue();
}
