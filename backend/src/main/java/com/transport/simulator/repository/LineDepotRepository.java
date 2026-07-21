package com.transport.simulator.repository;

import com.transport.simulator.entity.LineDepot;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineDepotRepository extends JpaRepository<LineDepot, Long> {

    @EntityGraph(attributePaths = {"depot", "depot.station", "dispatchTerminalStation"})
    List<LineDepot> findAllByLineIdAndActiveTrueOrderByDispatchPriorityAsc(Long lineId);
}
