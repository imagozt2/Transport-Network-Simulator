package com.transport.simulator.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transport.simulator.dto.response.stationoperation.StationArrivalResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationDevicesResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationDirectionResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationLineResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationsResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationsSummaryResponse;
import com.transport.simulator.dto.response.stationoperation.StationOperationTerminalResponse;
import com.transport.simulator.enums.ServiceDirection;
import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.StationOperationStatus;
import com.transport.simulator.service.StationOperationsQueryService;
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
class StationOperationsControllerTests {

    @Mock
    private StationOperationsQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StationOperationsController(queryService)).build();
    }

    @Test
    void shouldExposeStationStateAndSecondPrecisionArrivals() throws Exception {
        ZonedDateTime evaluatedAt = ZonedDateTime.of(
                2026, 7, 22, 8, 30, 0, 0, ZoneId.of("Europe/Madrid")
        );
        StationOperationTerminalResponse first = new StationOperationTerminalResponse(1L, "STA", "Estación A");
        StationOperationTerminalResponse last = new StationOperationTerminalResponse(3L, "STC", "Estación C");
        StationOperationLineResponse line = new StationOperationLineResponse(
                10L, "L3", "Línea 3", "Amarilla", 2,
                ServiceOperationPhase.OPERATING, true, 4, first, last,
                List.of(
                        new StationOperationDirectionResponse(ServiceDirection.OUTBOUND, last, 3),
                        new StationOperationDirectionResponse(ServiceDirection.INBOUND, first, 1)
                )
        );
        StationArrivalResponse arrival = new StationArrivalResponse(
                90L, "T-9001", "9000", 10L, "L3", "Línea 3", "Amarilla",
                ServiceDirection.OUTBOUND, last, 1, 45, evaluatedAt.plusSeconds(45), false
        );
        StationOperationResponse station = new StationOperationResponse(
                2L, "STB", "Estación B", StationOperationStatus.NORMAL,
                false, 1, 1, 4,
                new StationOperationDevicesResponse(3, 1, 1, 1, 3, 0, 0, 0),
                List.of(line), List.of(arrival)
        );
        when(queryService.getOperations()).thenReturn(new StationOperationsResponse(
                evaluatedAt, ServiceOperationPhase.OPERATING, 1, 1,
                new StationOperationsSummaryResponse(1, 1, 0, 1, 1, 1),
                List.of(station)
        ));

        mockMvc.perform(get("/api/stations/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("OPERATING"))
                .andExpect(jsonPath("$.activeStationCount").value(1))
                .andExpect(jsonPath("$.summary.stationCount").value(1))
                .andExpect(jsonPath("$.summary.transferStationCount").value(0))
                .andExpect(jsonPath("$.summary.ticketMachineCount").value(1))
                .andExpect(jsonPath("$.summary.entryValidatorCount").value(1))
                .andExpect(jsonPath("$.summary.exitValidatorCount").value(1))
                .andExpect(jsonPath("$.stations[0].code").value("STB"))
                .andExpect(jsonPath("$.stations[0].status").value("NORMAL"))
                .andExpect(jsonPath("$.stations[0].lineCount").value(1))
                .andExpect(jsonPath("$.stations[0].activeLineCount").value(1))
                .andExpect(jsonPath("$.stations[0].activeTrainCount").value(4))
                .andExpect(jsonPath("$.stations[0].lines[0].color").value("Amarilla"))
                .andExpect(jsonPath("$.stations[0].lines[0].activeTrainCount").value(4))
                .andExpect(jsonPath("$.stations[0].lines[0].directions[0].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.stations[0].lines[0].directions[0].destination.code").value("STC"))
                .andExpect(jsonPath("$.stations[0].lines[0].directions[0].activeTrainCount").value(3))
                .andExpect(jsonPath("$.stations[0].lines[0].directions[1].activeTrainCount").value(1))
                .andExpect(jsonPath("$.stations[0].nextArrivals[0].trainCode").value("T-9001"))
                .andExpect(jsonPath("$.stations[0].nextArrivals[0].secondsUntilArrival").value(45))
                .andExpect(jsonPath("$.stations[0].nextArrivals[0].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.stations[0].nextArrivals[0].destination.code").value("STC"));

        verify(queryService).getOperations();
    }
}
