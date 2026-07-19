package com.transport.simulator.repository;

import com.transport.simulator.entity.TrainModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainModelRepository extends JpaRepository<TrainModel, Long> {
}
