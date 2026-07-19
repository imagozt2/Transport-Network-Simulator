package com.transport.simulator.repository;

import com.transport.simulator.entity.Station;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationRepository extends JpaRepository<Station, Long> {

    long countByActiveTrue();

    List<Station> findAllByActiveTrueOrderByNameAsc();

    Optional<Station> findByCodeAndActiveTrue(String code);
}
