package com.transport.simulator.service;

import com.transport.simulator.dto.response.stationoperation.StationOperationDevicesResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationDirectionResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationLineResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationsResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationsSummaryResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationTerminalResponse;
import com.transport.simulator.dto.response.stationoperation.StationArrivalResponse;
import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.Station;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.StationOperationStatus;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.projection.StationDeviceSummaryProjection;
import com.transport.simulator.service.model.RailwaySimulationState;
import com.transport.simulator.service.model.SimulatedLineState;
import com.transport.simulator.service.model.SimulatedTrainState;
import com.transport.simulator.service.model.TrainArrivalEstimate;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StationOperationsQueryService {

    private static final int ARRIVALS_PER_LINE_AND_DIRECTION = 2;

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
        Map<Long, List<SimulatedTrainState>> activeTrainsByLine = simulation.trains().stream()
                .filter(train -> train.status() == TrainStatus.IN_SERVICE)
                .collect(Collectors.groupingBy(
                        train -> train.currentLineId(),
                        Collectors.toList()
                ));
        Map<Long, StationOperationDevicesResponse> devicesByStation = summarizeDevicesByStation(
                deviceRepository.summarizeActiveDevicesByStation()
        );

        List<StationOperationResponse> responses = stations.stream()
                .map(station -> toStationResponse(
                        station,
                        membershipsByStation.getOrDefault(station.getId(), List.of()),
                        routesByLine,
                        simulatedLinesById,
                        activeTrainsByLine,
                        devicesByStation.getOrDefault(station.getId(), StationOperationDevicesResponse.empty()),
                        simulation.evaluatedAt()
                ))
                .toList();
        int activeStationCount = Math.toIntExact(responses.stream()
                .filter(station -> station.activeLineCount() > 0)
                .count());
        int transferStationCount = Math.toIntExact(responses.stream()
                .filter(StationOperationResponse::transferStation)
                .count());
        long ticketMachineCount = responses.stream()
                .map(StationOperationResponse::devices)
                .mapToLong(StationOperationDevicesResponse::ticketMachines)
                .sum();
        long entryValidatorCount = responses.stream()
                .map(StationOperationResponse::devices)
                .mapToLong(StationOperationDevicesResponse::entryValidators)
                .sum();
        long exitValidatorCount = responses.stream()
                .map(StationOperationResponse::devices)
                .mapToLong(StationOperationDevicesResponse::exitValidators)
                .sum();

        return new StationOperationsResponse(
                simulation.evaluatedAt(),
                simulation.phase(),
                responses.size(),
                activeStationCount,
                new StationOperationsSummaryResponse(
                        responses.size(),
                        activeStationCount,
                        transferStationCount,
                        ticketMachineCount,
                        entryValidatorCount,
                        exitValidatorCount
                ),
                responses
        );
    }

    private StationOperationResponse toStationResponse(
            Station station,
            List<LineStation> memberships,
            Map<Long, List<LineStation>> routesByLine,
            Map<Long, SimulatedLineState> simulatedLinesById,
            Map<Long, List<SimulatedTrainState>> activeTrainsByLine,
            StationOperationDevicesResponse deviceSummary,
            ZonedDateTime evaluatedAt
    ) {
        List<StationOperationLineResponse> lines = memberships.stream()
                .map(membership -> toLineResponse(
                        membership,
                        requiredRoute(membership, routesByLine),
                        requiredSimulationLine(membership, simulatedLinesById),
                        activeTrainsByLine.getOrDefault(membership.getLine().getId(), List.of())
                ))
                .toList();
        int activeLineCount = Math.toIntExact(lines.stream()
                .filter(StationOperationLineResponse::serviceOpen)
                .count());
        int activeTrainCount = lines.stream()
                .filter(StationOperationLineResponse::serviceOpen)
                .mapToInt(StationOperationLineResponse::activeTrainCount)
                .sum();
        List<StationArrivalResponse> nextArrivals = calculateNextArrivals(
                station,
                memberships,
                routesByLine,
                activeTrainsByLine,
                evaluatedAt
        );

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
                lines,
                nextArrivals
        );
    }

    private List<StationArrivalResponse> calculateNextArrivals(
            Station station,
            List<LineStation> memberships,
            Map<Long, List<LineStation>> routesByLine,
            Map<Long, List<SimulatedTrainState>> activeTrainsByLine,
            ZonedDateTime evaluatedAt
    ) {
        List<StationArrivalResponse> arrivals = memberships.stream()
                .flatMap(membership -> {
                    List<LineStation> route = requiredRoute(membership, routesByLine);
                    return activeTrainsByLine.getOrDefault(membership.getLine().getId(), List.of())
                            .stream()
                            .map(train -> calculateArrival(station, membership, route, train, evaluatedAt));
                })
                .toList();

        return arrivals.stream()
                .collect(Collectors.groupingBy(arrival -> new ArrivalGroup(
                        arrival.lineId(),
                        arrival.direction()
                )))
                .values()
                .stream()
                .flatMap(group -> group.stream()
                        .sorted(Comparator.comparingLong(StationArrivalResponse::secondsUntilArrival)
                                .thenComparing(StationArrivalResponse::trainCode))
                        .limit(ARRIVALS_PER_LINE_AND_DIRECTION))
                .sorted(Comparator.comparingLong(StationArrivalResponse::secondsUntilArrival)
                        .thenComparing(StationArrivalResponse::lineCode)
                        .thenComparing(StationArrivalResponse::trainCode))
                .toList();
    }

    private StationArrivalResponse calculateArrival(
            Station station,
            LineStation membership,
            List<LineStation> route,
            SimulatedTrainState train,
            ZonedDateTime evaluatedAt
    ) {
        TrainArrivalEstimate estimate = TrainArrivalEstimator.estimate(station.getId(), route, train);
        LineStation destinationStop = estimate.direction() == ServiceDirection.OUTBOUND
                ? route.getLast()
                : route.getFirst();

        return new StationArrivalResponse(
                train.trainId(),
                train.trainCode(),
                train.trainSeries(),
                membership.getLine().getId(),
                membership.getLine().getCode(),
                membership.getLine().getName(),
                membership.getLine().getColor(),
                estimate.direction(),
                toTerminal(destinationStop),
                estimate.stationsAway(),
                estimate.secondsUntilArrival(),
                evaluatedAt.plusSeconds(estimate.secondsUntilArrival()),
                estimate.secondsUntilArrival() == 0
        );
    }

    private StationOperationLineResponse toLineResponse(
            LineStation membership,
            List<LineStation> route,
            SimulatedLineState simulatedLine,
            List<SimulatedTrainState> activeTrains
    ) {
        StationOperationTerminalResponse firstTerminal = toTerminal(route.getFirst());
        StationOperationTerminalResponse lastTerminal = toTerminal(route.getLast());
        return new StationOperationLineResponse(
                membership.getLine().getId(),
                membership.getLine().getCode(),
                membership.getLine().getName(),
                membership.getLine().getColor(),
                membership.getStationOrder(),
                simulatedLine.operation().phase(),
                simulatedLine.operation().serviceOpen(),
                activeTrains.size(),
                firstTerminal,
                lastTerminal,
                List.of(
                        toDirectionResponse(ServiceDirection.OUTBOUND, lastTerminal, activeTrains),
                        toDirectionResponse(ServiceDirection.INBOUND, firstTerminal, activeTrains)
                )
        );
    }

    private StationOperationDirectionResponse toDirectionResponse(
            ServiceDirection direction,
            StationOperationTerminalResponse destination,
            List<SimulatedTrainState> activeTrains
    ) {
        int activeTrainCount = Math.toIntExact(activeTrains.stream()
                .filter(train -> train.direction() == direction)
                .count());
        return new StationOperationDirectionResponse(direction, destination, activeTrainCount);
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

    private Map<Long, StationOperationDevicesResponse> summarizeDevicesByStation(
            List<StationDeviceSummaryProjection> summaries
    ) {
        Map<Long, DeviceSummaryAccumulator> accumulators = new java.util.HashMap<>();
        summaries.forEach(summary -> accumulators
                .computeIfAbsent(summary.getStationId(), ignored -> new DeviceSummaryAccumulator())
                .add(summary));
        return accumulators.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().toResponse()
        ));
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

    private record ArrivalGroup(Long lineId, ServiceDirection direction) {
    }

    private static final class DeviceSummaryAccumulator {
        private long total;
        private long ticketMachines;
        private long entryValidators;
        private long exitValidators;
        private long online;
        private long offline;
        private long maintenance;
        private long errors;

        private void add(StationDeviceSummaryProjection summary) {
            long count = summary.getTotal();
            total += count;
            switch (summary.getType()) {
                case TICKET_MACHINE -> ticketMachines += count;
                case ENTRY_VALIDATOR -> entryValidators += count;
                case EXIT_VALIDATOR -> exitValidators += count;
            }
            switch (summary.getStatus()) {
                case ONLINE -> online += count;
                case OFFLINE -> offline += count;
                case MAINTENANCE -> maintenance += count;
                case ERROR -> errors += count;
            }
        }

        private StationOperationDevicesResponse toResponse() {
            return new StationOperationDevicesResponse(
                    total, ticketMachines, entryValidators, exitValidators,
                    online, offline, maintenance, errors
            );
        }
    }
}
