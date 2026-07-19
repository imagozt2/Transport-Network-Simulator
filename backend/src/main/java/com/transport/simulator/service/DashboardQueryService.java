package com.transport.simulator.service;

import com.transport.simulator.dto.response.dashboard.DashboardDepotResponse;
import com.transport.simulator.dto.response.dashboard.DashboardDepotsResponse;
import com.transport.simulator.dto.response.dashboard.DashboardDevicesResponse;
import com.transport.simulator.dto.response.dashboard.DashboardFleetResponse;
import com.transport.simulator.dto.response.dashboard.DashboardLineResponse;
import com.transport.simulator.dto.response.dashboard.DashboardNetworkResponse;
import com.transport.simulator.dto.response.dashboard.DashboardResponse;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.DepotRepository;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TrainRepository;
import com.transport.simulator.repository.TransportLineRepository;
import com.transport.simulator.repository.projection.DepotOccupancyProjection;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

    private final StationRepository stationRepository;
    private final TransportLineRepository transportLineRepository;
    private final DeviceRepository deviceRepository;
    private final TrainRepository trainRepository;
    private final DepotRepository depotRepository;

    public DashboardQueryService(
            StationRepository stationRepository,
            TransportLineRepository transportLineRepository,
            DeviceRepository deviceRepository,
            TrainRepository trainRepository,
            DepotRepository depotRepository
    ) {
        this.stationRepository = stationRepository;
        this.transportLineRepository = transportLineRepository;
        this.deviceRepository = deviceRepository;
        this.trainRepository = trainRepository;
        this.depotRepository = depotRepository;
    }

    public DashboardResponse getSummary() {
        List<TransportLine> activeLines = transportLineRepository.findAllByActiveTrueOrderByCodeAsc();

        return new DashboardResponse(
                new DashboardNetworkResponse(
                        stationRepository.countByActiveTrue(),
                        activeLines.size()
                ),
                buildFleetSummary(),
                buildDeviceSummary(),
                buildDepotSummary(),
                activeLines.stream().map(this::toLineResponse).toList()
        );
    }

    private DashboardFleetResponse buildFleetSummary() {
        Map<TrainStatus, Long> byStatus = initializeEnumMap(TrainStatus.class);
        trainRepository.countActiveTrainsByStatus()
                .forEach(result -> byStatus.put(result.getStatus(), result.getTotal()));

        return new DashboardFleetResponse(
                trainRepository.countByActiveTrue(),
                Map.copyOf(byStatus)
        );
    }

    private DashboardDevicesResponse buildDeviceSummary() {
        Map<DeviceStatus, Long> byStatus = initializeEnumMap(DeviceStatus.class);
        deviceRepository.countActiveDevicesByStatus()
                .forEach(result -> byStatus.put(result.getStatus(), result.getTotal()));

        Map<DeviceType, Long> byType = initializeEnumMap(DeviceType.class);
        deviceRepository.countActiveDevicesByType()
                .forEach(result -> byType.put(result.getType(), result.getTotal()));

        return new DashboardDevicesResponse(
                deviceRepository.countByActiveTrue(),
                Map.copyOf(byStatus),
                Map.copyOf(byType)
        );
    }

    private DashboardDepotsResponse buildDepotSummary() {
        List<DashboardDepotResponse> depots = depotRepository.findActiveDepotOccupancy()
                .stream()
                .map(this::toDepotResponse)
                .toList();

        long totalCapacity = depots.stream().mapToLong(DashboardDepotResponse::capacity).sum();
        long assignedTrains = depots.stream().mapToLong(DashboardDepotResponse::assignedTrains).sum();
        long freeSlots = totalCapacity - assignedTrains;
        int occupationPercentage = totalCapacity == 0
                ? 0
                : (int) Math.round(assignedTrains * 100.0 / totalCapacity);

        return new DashboardDepotsResponse(
                depots.size(),
                totalCapacity,
                assignedTrains,
                freeSlots,
                occupationPercentage,
                depots
        );
    }

    private DashboardDepotResponse toDepotResponse(DepotOccupancyProjection depot) {
        return new DashboardDepotResponse(
                depot.getId(),
                depot.getCode(),
                depot.getName(),
                depot.getCapacity(),
                depot.getAssignedTrains(),
                depot.getCapacity() - depot.getAssignedTrains()
        );
    }

    private DashboardLineResponse toLineResponse(TransportLine line) {
        return new DashboardLineResponse(line.getId(), line.getCode(), line.getName(), line.getColor());
    }

    private <E extends Enum<E>> Map<E, Long> initializeEnumMap(Class<E> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .collect(
                        () -> new EnumMap<>(enumType),
                        (map, value) -> map.put(value, 0L),
                        Map::putAll
                );
    }
}
