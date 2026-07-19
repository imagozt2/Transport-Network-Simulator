package com.transport.simulator.repository;

import com.transport.simulator.entity.Train;
import com.transport.simulator.repository.projection.TrainStatusCountProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrainRepository extends JpaRepository<Train, Long> {

    long countByActiveTrue();

    @Query("""
            SELECT train.status AS status, COUNT(train.id) AS total
            FROM Train train
            WHERE train.active = true
            GROUP BY train.status
            """)
    List<TrainStatusCountProjection> countActiveTrainsByStatus();
}
