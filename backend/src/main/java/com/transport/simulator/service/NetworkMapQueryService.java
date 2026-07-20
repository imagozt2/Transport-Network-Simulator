package com.transport.simulator.service;

import com.transport.simulator.dto.response.networkmap.NetworkMapLineResponse;
import com.transport.simulator.dto.response.networkmap.NetworkMapResponse;
import com.transport.simulator.dto.response.networkmap.NetworkMapStationResponse;
import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.TransportLineRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NetworkMapQueryService {

    private final TransportLineRepository transportLineRepository;
    private final LineStationRepository lineStationRepository;

    public NetworkMapQueryService(
            TransportLineRepository transportLineRepository,
            LineStationRepository lineStationRepository
    ) {
        this.transportLineRepository = transportLineRepository;
        this.lineStationRepository = lineStationRepository;
    }

    public NetworkMapResponse getNetworkMap() {
        List<NetworkMapLineResponse> lines = transportLineRepository
                .findAllByActiveTrueOrderByCodeAsc()
                .stream()
                .map(this::toLineResponse)
                .toList();

        return new NetworkMapResponse(lines);
    }

    private NetworkMapLineResponse toLineResponse(TransportLine line) {
        List<NetworkMapStationResponse> stations = lineStationRepository
                .findAllByLineIdAndActiveTrueOrderByStationOrderAsc(line.getId())
                .stream()
                .filter(lineStation -> lineStation.getStation().isActive())
                .map(this::toStationResponse)
                .toList();

        return new NetworkMapLineResponse(
                line.getId(),
                line.getCode(),
                line.getName(),
                line.getColor(),
                stations
        );
    }

    private NetworkMapStationResponse toStationResponse(LineStation lineStation) {
        return new NetworkMapStationResponse(
                lineStation.getStation().getId(),
                lineStation.getStation().getCode(),
                lineStation.getStation().getName(),
                lineStation.getStationOrder()
        );
    }
}
