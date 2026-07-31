package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.service.model.NetworkJourney;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NetworkJourneyPlanningServiceTests {

    @Mock
    private StationRepository stationRepository;

    @Mock
    private LineStationRepository lineStationRepository;

    private NetworkJourneyPlanningService service;

    @BeforeEach
    void setUp() {
        service = new NetworkJourneyPlanningService(stationRepository, lineStationRepository);
    }

    @Test
    void shouldPreferFewerTransfersEvenWhenAnotherJourneyIsFaster() {
        Station origin = station(1L, "STA", "Origen");
        Station intermediate = station(2L, "STB", "Intermedia");
        Station transfer = station(3L, "STC", "Transbordo");
        Station destination = station(4L, "STD", "Destino");
        TransportLine direct = line(10L, "L1", "Línea directa", "Roja");
        TransportLine firstConnection = line(20L, "L2", "Línea rápida norte", "Verde");
        TransportLine secondConnection = line(30L, "L3", "Línea rápida sur", "Amarilla");
        stubStations(origin, destination);
        when(lineStationRepository.findAllByActiveTrueOrderByLineCodeAscStationOrderAsc())
                .thenReturn(List.of(
                        stop(direct, origin, 1, 300),
                        stop(direct, intermediate, 2, 300),
                        stop(direct, destination, 3, null),
                        stop(firstConnection, origin, 1, 100),
                        stop(firstConnection, transfer, 2, null),
                        stop(secondConnection, transfer, 1, 100),
                        stop(secondConnection, destination, 2, null)
                ));

        NetworkJourney journey = service.calculate(" sta ", "std");

        assertThat(journey.transferCount()).isZero();
        assertThat(journey.estimatedDurationSeconds()).isEqualTo(600);
        assertThat(journey.stations()).extracting(NetworkJourney.Station::code)
                .containsExactly("STA", "STB", "STD");
        assertThat(journey.segments()).singleElement().satisfies(segment -> {
            assertThat(segment.lineCode()).isEqualTo("L1");
            assertThat(segment.stopCount()).isEqualTo(2);
            assertThat(segment.travelSeconds()).isEqualTo(600);
        });
    }

    @Test
    void shouldAddTransferTimeAndSplitTheJourneyIntoLineSegments() {
        Station origin = station(1L, "STA", "Origen");
        Station transfer = station(2L, "STB", "Transbordo");
        Station destination = station(3L, "STC", "Destino");
        TransportLine firstLine = line(10L, "L1", "Línea 1", "Roja");
        TransportLine secondLine = line(20L, "L2", "Línea 2", "Verde");
        stubStations(origin, destination);
        when(lineStationRepository.findAllByActiveTrueOrderByLineCodeAscStationOrderAsc())
                .thenReturn(List.of(
                        stop(firstLine, origin, 1, 90),
                        stop(firstLine, transfer, 2, null),
                        stop(secondLine, transfer, 1, 120),
                        stop(secondLine, destination, 2, null)
                ));

        NetworkJourney journey = service.calculate("STA", "STC");

        assertThat(journey.transferCount()).isOne();
        assertThat(journey.estimatedDurationSeconds()).isEqualTo(390);
        assertThat(journey.stationCount()).isEqualTo(3);
        assertThat(journey.segments()).extracting(NetworkJourney.LineSegment::lineCode)
                .containsExactly("L1", "L2");
        assertThat(journey.segments().getFirst().destination().code()).isEqualTo("STB");
        assertThat(journey.segments().getLast().origin().code()).isEqualTo("STB");
    }

    @Test
    void shouldReturnAZeroLengthJourneyForTheSameStation() {
        Station station = station(1L, "STA", "Estación A");
        when(stationRepository.findByCodeAndActiveTrue("STA")).thenReturn(Optional.of(station));

        NetworkJourney journey = service.calculate("STA", "STA");

        assertThat(journey.stationCount()).isOne();
        assertThat(journey.transferCount()).isZero();
        assertThat(journey.estimatedDurationSeconds()).isZero();
        assertThat(journey.segments()).isEmpty();
    }

    @Test
    void shouldRejectStationsThatAreNotConnected() {
        Station origin = station(1L, "STA", "Origen");
        Station destination = station(2L, "STB", "Destino");
        TransportLine line = line(10L, "L1", "Línea 1", "Roja");
        stubStations(origin, destination);
        when(lineStationRepository.findAllByActiveTrueOrderByLineCodeAscStationOrderAsc())
                .thenReturn(List.of(stop(line, origin, 1, null)));

        assertThatThrownBy(() -> service.calculate("STA", "STB"))
                .isInstanceOf(ServiceConfigurationException.class)
                .hasMessageContaining("No journey connects STA and STB");
    }

    private void stubStations(Station origin, Station destination) {
        when(stationRepository.findByCodeAndActiveTrue(origin.getCode())).thenReturn(Optional.of(origin));
        when(stationRepository.findByCodeAndActiveTrue(destination.getCode())).thenReturn(Optional.of(destination));
    }

    private Station station(Long id, String code, String name) {
        Station station = new Station(code, name);
        ReflectionTestUtils.setField(station, "id", id);
        return station;
    }

    private TransportLine line(Long id, String code, String name, String color) {
        TransportLine line = new TransportLine(code, name, color);
        ReflectionTestUtils.setField(line, "id", id);
        return line;
    }

    private LineStation stop(
            TransportLine line,
            Station station,
            int order,
            Integer travelSecondsToNext
    ) {
        LineStation stop = new LineStation(line, station, order);
        ReflectionTestUtils.setField(stop, "travelSecondsToNext", travelSecondsToNext);
        return stop;
    }
}
