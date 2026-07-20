package com.transport.simulator.repository;

import com.transport.simulator.entity.LineStation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineStationRepository extends JpaRepository<LineStation, Long> {

    List<LineStation> findAllByActiveTrueOrderByLineCodeAscStationOrderAsc();

    List<LineStation> findAllByLineIdAndActiveTrueOrderByStationOrderAsc(Long lineId);
}
