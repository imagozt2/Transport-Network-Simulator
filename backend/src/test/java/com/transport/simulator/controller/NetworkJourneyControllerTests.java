package com.transport.simulator.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transport.simulator.service.NetworkJourneyPlanningService;
import com.transport.simulator.service.ServiceConfigurationException;
import com.transport.simulator.service.model.NetworkJourney;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class NetworkJourneyControllerTests {

    @Mock
    private NetworkJourneyPlanningService journeyPlanningService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NetworkJourneyController(journeyPlanningService))
                .build();
    }

    @Test
    void shouldExposeTheCalculatedJourneyContract() throws Exception {
        NetworkJourney.Station origin = station(1L, "ST001", "Aeropuerto");
        NetworkJourney.Station transfer = station(2L, "ST002", "Puerto Fluvial");
        NetworkJourney.Station destination = station(3L, "ST003", "Los Molinos");
        NetworkJourney.LineSegment firstSegment = new NetworkJourney.LineSegment(
                4L, "L4", "Linea 4", "Morada", origin, transfer, 1, 120, List.of(origin, transfer)
        );
        NetworkJourney.LineSegment secondSegment = new NetworkJourney.LineSegment(
                1L, "L1", "Linea 1", "Roja", transfer, destination, 1, 150, List.of(transfer, destination)
        );
        when(journeyPlanningService.calculate("ST001", "ST003")).thenReturn(new NetworkJourney(
                origin, destination, 3, 1, 450,
                List.of(origin, transfer, destination),
                List.of(firstSegment, secondSegment)
        ));

        mockMvc.perform(get("/api/network-map/journeys")
                        .param("originStationCode", "ST001")
                        .param("destinationStationCode", "ST003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin.code").value("ST001"))
                .andExpect(jsonPath("$.destination.code").value("ST003"))
                .andExpect(jsonPath("$.stationCount").value(3))
                .andExpect(jsonPath("$.transferCount").value(1))
                .andExpect(jsonPath("$.estimatedDurationSeconds").value(450))
                .andExpect(jsonPath("$.stations.length()").value(3))
                .andExpect(jsonPath("$.segments.length()").value(2))
                .andExpect(jsonPath("$.segments[0].lineCode").value("L4"))
                .andExpect(jsonPath("$.segments[0].stopCount").value(1))
                .andExpect(jsonPath("$.segments[1].lineColor").value("Roja"));

        verify(journeyPlanningService).calculate("ST001", "ST003");
    }

    @Test
    void shouldRejectUnknownStationsAsBadRequests() throws Exception {
        when(journeyPlanningService.calculate("UNKNOWN", "ST003"))
                .thenThrow(new IllegalArgumentException("Unknown origin station: UNKNOWN"));

        mockMvc.perform(get("/api/network-map/journeys")
                        .param("originStationCode", "UNKNOWN")
                        .param("destinationStationCode", "ST003"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExposeAZeroLengthJourneyForTheSameStation() throws Exception {
        NetworkJourney.Station station = station(1L, "ST001", "Aeropuerto");
        when(journeyPlanningService.calculate("ST001", "ST001"))
                .thenReturn(new NetworkJourney(
                        station, station, 1, 0, 0, List.of(station), List.of()
                ));

        mockMvc.perform(get("/api/network-map/journeys")
                        .param("originStationCode", "ST001")
                        .param("destinationStationCode", "ST001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationCount").value(1))
                .andExpect(jsonPath("$.transferCount").value(0))
                .andExpect(jsonPath("$.estimatedDurationSeconds").value(0))
                .andExpect(jsonPath("$.stations.length()").value(1))
                .andExpect(jsonPath("$.segments").isEmpty());
    }

    @Test
    void shouldReportUnavailableJourneysAsUnprocessable() throws Exception {
        when(journeyPlanningService.calculate("ST001", "ST003"))
                .thenThrow(new ServiceConfigurationException("No journey connects ST001 and ST003"));

        mockMvc.perform(get("/api/network-map/journeys")
                .param("originStationCode", "ST001")
                        .param("destinationStationCode", "ST003"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void shouldRequireBothStationCodes() throws Exception {
        mockMvc.perform(get("/api/network-map/journeys")
                        .param("originStationCode", "ST001"))
                .andExpect(status().isBadRequest());
    }

    private NetworkJourney.Station station(Long id, String code, String name) {
        return new NetworkJourney.Station(id, code, name);
    }
}
