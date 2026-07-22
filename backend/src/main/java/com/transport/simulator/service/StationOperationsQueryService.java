package com.transport.simulator.service;

import com.transport.simulator.dto.response.stationoperation.StationOperationDevicesResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationLineResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationsResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationTerminalResponse;
import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.Station;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.StationOperationStatus;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.service.model.RailwaySimulationState;
import com.transport.simulator.service.model.SimulatedLineState;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StationOperationsQueryService {

    private final RailwaySimulationStateService railwaySimulationStateService;
    private final StationRepository stationRepository;
    private final LineStationRepository lineStationRepository;
    private final DeviceRepository deviceRepository;

    public StationOperationsQueryService(
            RailwaySimulationStateService railwaySimulationStateService,
            StationRepository stationRepository,
            LineStationRepository lineStationRepository,
            DeviceRepository deviceRepository
    ) {
        this.railwaySimulationStateService = railwaySimulationStateService;
        this.stationRepository = stationRepository;
        this.lineStationRepository = lineStationRepository;
        this.deviceRepository = deviceRepository;
    }

    public StationOperationsResponse getOperations() {
        RailwaySimulationState simulation = railwaySimulationStateService.getCurrentState();
        List<Station> stations = stationRepository.findAllByActiveTrueOrderByNameAsc();
        List<LineStation> lineStations = lineStationRepository
                .findAllByActiveTrueOrderByLineCodeAscStationOrderAsc();

        Map<Long, SimulatedLineState> simulatedLinesById = simulation.lines().stream()
                .collect(Collectors.toUnmodifiableMap(
                        line -> line.operation().lineId(),
                        Function.identity()
                ));
        Map<Long, List<LineStation>> membershipsByStation = lineStations.stream()
                .collect(Collectors.groupingBy(lineStation -> lineStation.getStation().getId()));
        Map<Long, List<LineStation>> routesByLine = lineStations.stream()
                .collect(Collectors.groupingBy(lineStation -> lineStation.getLine().getId()));
        Map<Long, Long> activeTrainsByLine = simulation.trains().stream()
                .filter(train -> train.status() == TrainStatus.IN_SERVICE)
                .collect(Collectors.groupingBy(
                        train -> train.currentLineId(),
                        Collectors.counting()
                ));
        Map<Long, List<Device>> devicesByStation = deviceRepository.findAllByActiveTrueOrderByCodeAsc()
                .stream()
                .collect(Collectors.groupingBy(device -> device.getStation().getId()));

        List<StationOperationResponse> responses = stations.stream()
                .map(station -> toStationResponse(
                        station,
                        membershipsByStation.getOrDefault(station.getId(), List.of()),
                        routesByLine,
                        simulatedLinesById,
                        activeTrainsByLine,
                        devicesByStation.getOrDefault(station.getId(), List.of())
                ))
                .toList();
        int activeStationCount = Math.toIntExact(responses.stream()
                .filter(station -> station.activeLineCount() > 0)
                .count());

        return new StationOperationsResponse(
                simulation.evaluatedAt(),
                simulation.phase(),
                responses.size(),
                activeStationCount,
                responses
        );
    }

    private StationOperationResponse toStationResponse(
            Station station,
            List<LineStation> memberships,
            Map<Long, List<LineStation>> routesByLine,
            Map<Long, SimulatedLineState> simulatedLinesById,
            Map<Long, Long> activeTrainsByLine,
            List<Device> devices
    ) {
        List<StationOperationLineResponse> lines = memberships.stream()
                .map(membership -> toLineResponse(
                        membership,
                        requiredRoute(membership, routesByLine),
                        requiredSimulationLine(membership, simulatedLinesById),
                        activeTrainsByLine.getOrDefault(membership.getLine().getId(), 0L)
                ))
                .toList();
        int activeLineCount = Math.toIntExact(lines.stream()
                .filter(StationOperationLineResponse::serviceOpen)
                .count());
        int activeTrainCount = lines.stream()
                .filter(StationOperationLineResponse::serviceOpen)
                .mapToInt(StationOperationLineResponse::activeTrainCount)
                .sum();
        StationOperationDevicesResponse deviceSummary = summarizeDevices(devices);

        return new StationOperationResponse(
                station.getId(),
                station.getCode(),
                station.getName(),
                resolveStatus(activeLineCount, activeTrainCount, deviceSummary),
                lines.size() > 1,
                lines.size(),
                activeLineCount,
                activeTrainCount,
                deviceSummary,
                lines
        );
    }

    private StationOperationLineResponse toLineResponse(
            LineStation membership,
            List<LineStation> route,
            SimulatedLineState simulatedLine,
            long activeTrainCount
    ) {
        return new StationOperationLineResponse(
                membership.getLine().getId(),
                membership.getLine().getCode(),
                membership.getLine().getName(),
                membership.getLine().getColor(),
                membership.getStationOrder(),
                simulatedLine.operation().phase(),
                simulatedLine.operation().serviceOpen(),
                Math.toIntExact(activeTrainCount),
                toTerminal(route.getFirst()),
                toTerminal(route.getLast())
        );
    }

    private StationOperationTerminalResponse toTerminal(LineStation lineStation) {
        return new StationOperationTerminalResponse(
                lineStation.getStation().getId(),
                lineStation.getStation().getCode(),
                lineStation.getStation().getName()
        );
    }

    private List<LineStation> requiredRoute(
            LineStation membership,
            Map<Long, List<LineStation>> routesByLine
    ) {
        List<LineStation> route = routesByLine.get(membership.getLine().getId());
        if (route == null || route.size() < 2) {
            throw new ServiceConfigurationException(
                    "Line " + membership.getLine().getCode() + " requires at least two active stations"
            );
        }
        return route;
    }

    private SimulatedLineState requiredSimulationLine(
            LineStation membership,
            Map<Long, SimulatedLineState> simulatedLinesById
    ) {
        SimulatedLineState line = simulatedLinesById.get(membership.getLine().getId());
        if (line == null) {
            throw new ServiceConfigurationException(
                    "Missing simulated state for line " + membership.getLine().getCode()
            );
        }
        return line;
    }

    private StationOperationDevicesResponse summarizeDevices(List<Device> devices) {
        return new StationOperationDevicesResponse(
                devices.size(),
                countType(devices, DeviceType.TICKET_MACHINE),
                countType(devices, DeviceType.ENTRY_VALIDATOR),
                countType(devices, DeviceType.EXIT_VALIDATOR),
                countStatus(devices, DeviceStatus.ONLINE),
                countStatus(devices, DeviceStatus.OFFLINE),
                countStatus(devices, DeviceStatus.MAINTENANCE),
                countStatus(devices, DeviceStatus.ERROR)
        );
    }

    private long countType(List<Device> devices, DeviceType type) {
        return devices.stream().filter(device -> device.getType() == type).count();
    }

    private long countStatus(List<Device> devices, DeviceStatus status) {
        return devices.stream().filter(device -> device.getStatus() == status).count();
    }

    private StationOperationStatus resolveStatus(
            int activeLineCount,
            int activeTrainCount,
            StationOperationDevicesResponse devices
    ) {
        if (activeLineCount == 0) {
            return StationOperationStatus.CLOSED;
        }
        if (devices.errors() > 0) {
            return StationOperationStatus.CRITICAL;
        }
        if (devices.offline() > 0 || devices.maintenance() > 0) {
            return StationOperationStatus.DEGRADED;
        }
        if (activeTrainCount == 0) {
            return StationOperationStatus.NO_TRAINS;
        }
        return StationOperationStatus.NORMAL;
    }
}
