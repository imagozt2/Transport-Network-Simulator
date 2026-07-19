package com.transport.simulator.repository;

import com.transport.simulator.entity.TransportLine;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportLineRepository extends JpaRepository<TransportLine, Long> {

    long countByActiveTrue();

    List<TransportLine> findAllByActiveTrueOrderByCodeAsc();

    Optional<TransportLine> findByCodeAndActiveTrue(String code);
}
