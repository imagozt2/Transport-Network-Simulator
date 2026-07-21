package com.transport.simulator.repository;

import com.transport.simulator.entity.LineStation;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineStationRepository extends JpaRepository<LineStation, Long> {

    @EntityGraph(attributePaths = {"line", "station"})
    List<LineStation> findAllByActiveTrueOrderByLineCodeAscStationOrderAsc();

    @EntityGraph(attributePaths = "station")
    List<LineStation> findAllByLineIdAndActiveTrueOrderByStationOrderAsc(Long lineId);
}
