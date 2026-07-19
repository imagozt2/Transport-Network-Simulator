package com.transport.simulator.repository;

import com.transport.simulator.entity.Depot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotRepository extends JpaRepository<Depot, Long> {

    long countByActiveTrue();

    List<Depot> findAllByActiveTrueOrderByCodeAsc();
}
