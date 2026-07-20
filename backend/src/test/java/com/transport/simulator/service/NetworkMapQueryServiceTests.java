package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.response.networkmap.NetworkMapResponse;
import com.transport.simulator.entity.LineStation;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.TransportLine;
import com.transport.simulator.repository.LineStationRepository;
import com.transport.simulator.repository.TransportLineRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NetworkMapQueryServiceTests {

    @Mock
    private TransportLineRepository transportLineRepository;

    @Mock
    private LineStationRepository lineStationRepository;

    private NetworkMapQueryService networkMapQueryService;

    @BeforeEach
    void setUp() {
        networkMapQueryService = new NetworkMapQueryService(
                transportLineRepository,
                lineStationRepository
        );
    }

    @Test
    void shouldBuildLinesWithTheirActiveStationsInRouteOrder() {
        TransportLine line = line(1L, "L1", "Línea 1", "Roja");
        Station firstStation = station(10L, "ST010", "Estación Norte", true);
        Station secondStation = station(20L, "ST020", "Estación Centro", true);
        Station inactiveStation = station(30L, "ST030", "Estación cerrada", false);

        when(transportLineRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(line));
        when(lineStationRepository.findAllByLineIdAndActiveTrueOrderByStationOrderAsc(1L))
                .thenReturn(List.of(
                        new LineStation(line, firstStation, 1),
                        new LineStation(line, secondStation, 2),
                        new LineStation(line, inactiveStation, 3)
                ));

        NetworkMapResponse result = networkMapQueryService.getNetworkMap();

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().code()).isEqualTo("L1");
        assertThat(result.lines().getFirst().stations())
                .extracting("id", "code", "stationOrder")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, "ST010", 1),
                        org.assertj.core.groups.Tuple.tuple(20L, "ST020", 2)
                );
        verify(lineStationRepository).findAllByLineIdAndActiveTrueOrderByStationOrderAsc(1L);
    }

    @Test
    void shouldIncludeAnActiveLineWithoutStations() {
        TransportLine line = line(2L, "L2", "Línea 2", "Verde");
        when(transportLineRepository.findAllByActiveTrueOrderByCodeAsc()).thenReturn(List.of(line));
        when(lineStationRepository.findAllByLineIdAndActiveTrueOrderByStationOrderAsc(2L))
                .thenReturn(List.of());

        NetworkMapResponse result = networkMapQueryService.getNetworkMap();

        assertThat(result.lines()).singleElement().satisfies(response -> {
            assertThat(response.code()).isEqualTo("L2");
            assertThat(response.stations()).isEmpty();
        });
    }

    private TransportLine line(Long id, String code, String name, String color) {
        TransportLine line = new TransportLine(code, name, color);
        ReflectionTestUtils.setField(line, "id", id);
        return line;
    }

    private Station station(Long id, String code, String name, boolean active) {
        Station station = new Station(code, name);
        ReflectionTestUtils.setField(station, "id", id);
        ReflectionTestUtils.setField(station, "active", active);
        return station;
    }
}
