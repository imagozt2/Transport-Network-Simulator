package com.transport.simulator.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transport.simulator.dto.response.lineoperation.LineOperationDepotResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationDepotStationResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationStationResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationTrainResponse;
import com.transport.simulator.dto.response.lineoperation.LineOperationsResponse;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.ServicePeriodType;
import com.transport.simulator.enums.TrainPositionState;
import com.transport.simulator.service.LineOperationsQueryService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LineOperationsControllerTests {

    @Mock
    private LineOperationsQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LineOperationsController(queryService)).build();
    }

    @Test
    void shouldExposeTheOperationalSummaryWithStationsAndLiveTrains() throws Exception {
        ZonedDateTime evaluatedAt = ZonedDateTime.of(2026, 7, 21, 8, 30, 0, 0, ZoneId.of("Europe/Madrid"));
        LineOperationStationResponse first = new LineOperationStationResponse(1L, "ST001", "Aeropuerto", 1);
        LineOperationStationResponse last = new LineOperationStationResponse(2L, "ST002", "Centro", 2);
        LineOperationTrainResponse train = new LineOperationTrainResponse(
                90L, "T-9001", "9000", 1, TrainPositionState.BETWEEN_STATIONS,
                ServiceDirection.OUTBOUND, null, null, 1L, "ST001", 2L, "ST002",
                40, 72L, evaluatedAt.plusSeconds(72)
        );
        LineOperationDepotResponse depot = new LineOperationDepotResponse(
                10L,
                "DEP-A",
                "Cochera de Aeropuerto",
                new LineOperationDepotStationResponse(1L, "ST001", "Aeropuerto"),
                new LineOperationDepotStationResponse(2L, "ST002", "Centro"),
                1,
                true,
                true,
                8,
                5,
                3
        );
        LineOperationResponse line = new LineOperationResponse(
                4L, "L4", "Línea 4", "Lila", ServiceOperationPhase.OPERATING, true,
                evaluatedAt.minusHours(2), evaluatedAt.plusHours(14), "PEAK_MORNING", ServicePeriodType.PEAK,
                240, 1_200L, 2, first, last, 1, List.of(depot), List.of(first, last), List.of(train)
        );
        when(queryService.getOperations()).thenReturn(new LineOperationsResponse(
                evaluatedAt, ServiceOperationPhase.OPERATING, 1, List.of(line)
        ));

        mockMvc.perform(get("/api/lines/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("OPERATING"))
                .andExpect(jsonPath("$.activeLineCount").value(1))
                .andExpect(jsonPath("$.lines[0].code").value("L4"))
                .andExpect(jsonPath("$.lines[0].headwaySeconds").value(240))
                .andExpect(jsonPath("$.lines[0].stationCount").value(2))
                .andExpect(jsonPath("$.lines[0].stations[0].code").value("ST001"))
                .andExpect(jsonPath("$.lines[0].activeTrainCount").value(1))
                .andExpect(jsonPath("$.lines[0].depots[0].code").value("DEP-A"))
                .andExpect(jsonPath("$.lines[0].depots[0].dispatchTerminal.code").value("ST002"))
                .andExpect(jsonPath("$.lines[0].depots[0].assignedTrainCount").value(8))
                .andExpect(jsonPath("$.lines[0].depots[0].trainsInService").value(5))
                .andExpect(jsonPath("$.lines[0].depots[0].availableTrainCount").value(3))
                .andExpect(jsonPath("$.lines[0].trains[0].code").value("T-9001"))
                .andExpect(jsonPath("$.lines[0].trains[0].progressPercentage").value(40))
                .andExpect(jsonPath("$.lines[0].trains[0].direction").value("OUTBOUND"));

        verify(queryService).getOperations();
    }
}
