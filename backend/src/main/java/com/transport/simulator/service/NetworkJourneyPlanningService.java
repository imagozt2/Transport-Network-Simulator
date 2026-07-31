package com.transport.simulator.service;

import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.service.model.NetworkJourney;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NetworkJourneyPlanningService {

    static final int TRANSFER_SECONDS = 180;

    private final StationRepository stationRepository;
    private final LineStationRepository lineStationRepository;

    public NetworkJourneyPlanningService(
            StationRepository stationRepository,
            LineStationRepository lineStationRepository
    ) {
        this.stationRepository = stationRepository;
        this.lineStationRepository = lineStationRepository;
    }

    public NetworkJourney calculate(String originCode, String destinationCode) {
        Station origin = requiredStation(originCode, "origin");
        Station destination = requiredStation(destinationCode, "destination");
        if (origin.getId().equals(destination.getId())) {
            NetworkJourney.Station station = toStation(origin);
            return new NetworkJourney(station, station, 1, 0, 0, List.of(station), List.of());
        }

        Graph graph = buildGraph();
        List<State> origins = graph.statesByStation().getOrDefault(origin.getId(), List.of());
        if (origins.isEmpty() || !graph.statesByStation().containsKey(destination.getId())) {
            throw noJourney(origin, destination);
        }

        Path path = shortestPath(origins, destination.getId(), graph.edges());
        if (path == null) { throw noJourney(origin, destination); }
        return toJourney(origin, destination, path);
    }

    private Graph buildGraph() {
        List<LineStation> memberships = lineStationRepository
                .findAllByActiveTrueOrderByLineCodeAscStationOrderAsc()
                .stream()
                .filter(stop -> stop.getLine().isActive() && stop.getStation().isActive())
                .toList();
        Map<State, List<Edge>> edges = new HashMap<>();
        Map<Long, List<State>> statesByStation = new HashMap<>();
        Map<Long, List<LineStation>> stopsByLine = new HashMap<>();

        for (LineStation stop : memberships) {
            State state = new State(stop.getStation().getId(), stop.getLine().getId());
            edges.computeIfAbsent(state, ignored -> new ArrayList<>());
            statesByStation.computeIfAbsent(stop.getStation().getId(), ignored -> new ArrayList<>()).add(state);
            stopsByLine.computeIfAbsent(stop.getLine().getId(), ignored -> new ArrayList<>()).add(stop);
        }

        for (List<LineStation> route : stopsByLine.values()) {
            route.sort(Comparator.comparingInt(LineStation::getStationOrder));
            for (int index = 0; index < route.size() - 1; index++) {
                LineStation first = route.get(index);
                LineStation second = route.get(index + 1);
                Integer travelSeconds = first.getTravelSecondsToNext();
                if (travelSeconds == null || travelSeconds <= 0) {
                    throw new ServiceConfigurationException(
                            "Missing travel time between " + first.getStation().getCode()
                                    + " and " + second.getStation().getCode()
                                    + " on line " + first.getLine().getCode()
                    );
                }
                addEdge(edges, first, second, travelSeconds);
                addEdge(edges, second, first, travelSeconds);
            }
        }

        for (List<State> stationStates : statesByStation.values()) {
            for (State from : stationStates) {
                for (State to : stationStates) {
                    if (!from.equals(to)) {
                        edges.get(from).add(new Edge(to, null, null, TRANSFER_SECONDS, true));
                    }
                }
            }
        }
        return new Graph(edges, statesByStation);
    }

    private void addEdge(
            Map<State, List<Edge>> edges,
            LineStation from,
            LineStation to,
            int travelSeconds
    ) {
        State fromState = new State(from.getStation().getId(), from.getLine().getId());
        State toState = new State(to.getStation().getId(), to.getLine().getId());
        edges.get(fromState).add(new Edge(
                toState, to.getStation(), from.getLine(), travelSeconds, false
        ));
    }

    private Path shortestPath(List<State> origins, Long destinationId, Map<State, List<Edge>> graph) {
        Map<State, Cost> costs = new HashMap<>();
        Map<State, Previous> previous = new HashMap<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(Comparator.comparing(QueueEntry::cost));
        for (State origin : origins) {
            Cost cost = new Cost(0, 0, 0);
            costs.put(origin, cost);
            queue.add(new QueueEntry(origin, cost));
        }

        State destination = null;
        while (!queue.isEmpty()) {
            QueueEntry current = queue.remove();
            if (!current.cost().equals(costs.get(current.state()))) { continue; }
            if (current.state().stationId().equals(destinationId)) {
                destination = current.state();
                break;
            }
            for (Edge edge : graph.getOrDefault(current.state(), List.of())) {
                Cost candidate = current.cost().plus(edge);
                if (candidate.compareTo(costs.get(edge.to())) < 0) {
                    costs.put(edge.to(), candidate);
                    previous.put(edge.to(), new Previous(current.state(), edge));
                    queue.add(new QueueEntry(edge.to(), candidate));
                }
            }
        }
        if (destination == null) { return null; }

        List<Edge> edges = new ArrayList<>();
        State cursor = destination;
        while (previous.containsKey(cursor)) {
            Previous step = previous.get(cursor);
            edges.add(step.edge());
            cursor = step.state();
        }
        java.util.Collections.reverse(edges);
        return new Path(costs.get(destination), edges);
    }

    private NetworkJourney toJourney(Station origin, Station destination, Path path) {
        List<NetworkJourney.Station> stations = new ArrayList<>();
        stations.add(toStation(origin));
        List<NetworkJourney.LineSegment> segments = new ArrayList<>();
        SegmentBuilder segment = null;

        for (Edge edge : path.edges()) {
            if (edge.transfer()) {
                if (segment != null) { segments.add(segment.build()); segment = null; }
                continue;
            }
            if (segment == null || !segment.line.getId().equals(edge.line().getId())) {
                if (segment != null) { segments.add(segment.build()); }
                segment = new SegmentBuilder(edge.line(), stations.getLast());
            }
            NetworkJourney.Station arrival = toStation(edge.station());
            segment.add(arrival, edge.seconds());
            if (!stations.getLast().id().equals(arrival.id())) { stations.add(arrival); }
        }
        if (segment != null) { segments.add(segment.build()); }

        return new NetworkJourney(
                toStation(origin),
                toStation(destination),
                stations.size(),
                path.cost().transfers(),
                path.cost().seconds(),
                stations,
                segments
        );
    }

    private Station requiredStation(String code, String role) {
        String normalized = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) { throw new IllegalArgumentException(role + " station code is required"); }
        return stationRepository.findByCodeAndActiveTrue(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + role + " station: " + normalized));
    }

    private ServiceConfigurationException noJourney(Station origin, Station destination) {
        return new ServiceConfigurationException(
                "No journey connects " + origin.getCode() + " and " + destination.getCode()
        );
    }

    private NetworkJourney.Station toStation(Station station) {
        return new NetworkJourney.Station(station.getId(), station.getCode(), station.getName());
    }

    private record State(Long stationId, Long lineId) {
    }

    private record Edge(State to, Station station, TransportLine line, int seconds, boolean transfer) {
    }

    private record Graph(Map<State, List<Edge>> edges, Map<Long, List<State>> statesByStation) {
    }

    private record Previous(State state, Edge edge) {
    }

    private record Path(Cost cost, List<Edge> edges) {
    }

    private record QueueEntry(State state, Cost cost) {
    }

    private record Cost(int transfers, int seconds, int stops) implements Comparable<Cost> {
        private Cost plus(Edge edge) {
            return new Cost(
                    transfers + (edge.transfer() ? 1 : 0),
                    seconds + edge.seconds(),
                    stops + (edge.transfer() ? 0 : 1)
            );
        }

        @Override
        public int compareTo(Cost other) {
            if (other == null) { return -1; }
            int byTransfers = Integer.compare(transfers, other.transfers);
            if (byTransfers != 0) { return byTransfers; }
            int bySeconds = Integer.compare(seconds, other.seconds);
            return bySeconds != 0 ? bySeconds : Integer.compare(stops, other.stops);
        }
    }

    private static final class SegmentBuilder {
        private final TransportLine line;
        private final List<NetworkJourney.Station> stations = new ArrayList<>();
        private int travelSeconds;

        private SegmentBuilder(TransportLine line, NetworkJourney.Station origin) {
            this.line = line;
            stations.add(origin);
        }

        private void add(NetworkJourney.Station station, int seconds) {
            stations.add(station);
            travelSeconds += seconds;
        }

        private NetworkJourney.LineSegment build() {
            return new NetworkJourney.LineSegment(
                    line.getId(), line.getCode(), line.getName(), line.getColor(),
                    stations.getFirst(), stations.getLast(), stations.size() - 1,
                    travelSeconds, stations
            );
        }
    }
}
