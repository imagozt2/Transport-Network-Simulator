package com.transport.simulator.repository;

import com.transport.simulator.entity.Depot;
import com.transport.simulator.repository.projection.DepotOccupancyProjection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepotRepository extends JpaRepository<Depot, Long> {

    long countByActiveTrue();

    @EntityGraph(attributePaths = "station")
    List<Depot> findAllByActiveTrueOrderByCodeAsc();

    @Query("""
            SELECT depot.id AS id,
                   depot.code AS code,
                   depot.name AS name,
                   depot.capacity AS capacity,
                   COUNT(train.id) AS assignedTrains
            FROM Depot depot
            LEFT JOIN Train train
                   ON train.homeDepot = depot AND train.active = true
            WHERE depot.active = true
            GROUP BY depot.id, depot.code, depot.name, depot.capacity
            ORDER BY depot.code
            """)
    List<DepotOccupancyProjection> findActiveDepotOccupancy();
}
