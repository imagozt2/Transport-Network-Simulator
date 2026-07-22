package com.transport.simulator.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transport.simulator.dto.response.depotoperation.DepotFleetDistributionResponse;
import com.transport.simulator.dto.response.depotoperation.DepotMovementLineResponse;
import com.transport.simulator.dto.response.depotoperation.DepotMovementResponse;
import com.transport.simulator.dto.response.depotoperation.DepotMovementsSummaryResponse;
import com.transport.simulator.dto.response.depotoperation.DepotMovementTrainResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationsResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationsSummaryResponse;
import com.transport.simulator.dto.response.depotoperation.DepotOperationStationResponse;
import com.transport.simulator.enums.DepotMovementStatus;
import com.transport.simulator.enums.DepotMovementType;
import com.transport.simulator.enums.DepotOperationStatus;
import com.transport.simulator.enums.FleetRole;
import com.transport.simulator.enums.ServiceOperationPhase;
import com.transport.simulator.enums.TrainStatus;
import com.transport.simulator.service.DepotOperationsQueryService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DepotOperationsControllerTests {

    @Mock private DepotOperationsQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DepotOperationsController(queryService)).build();
    }

    @Test
    void shouldExposeOccupancyAndDepotMovements() throws Exception {
        ZonedDateTime evaluatedAt = ZonedDateTime.of(
                2026, 7, 22, 8, 30, 0, 0, ZoneId.of("Europe/Madrid")
        );
        DepotOperationStationResponse station = new DepotOperationStationResponse(10L, "ST010", "Plaza de la Mina");
        DepotMovementsSummaryResponse movementSummary = new DepotMovementsSummaryResponse(
                1, 1, 0, 0, 1, evaluatedAt.plusMinutes(10)
        );
        DepotMovementResponse movement = new DepotMovementResponse(
                1, DepotMovementType.EXIT, DepotMovementStatus.SCHEDULED,
                evaluatedAt.plusMinutes(10), 600L,
                new DepotMovementTrainResponse(100L, "T-9001", "9000", FleetRole.REGULAR_SERVICE),
                new DepotMovementLineResponse(1L, "L1", "Línea 1", "Roja"), station
        );
        DepotFleetDistributionResponse fleet = new DepotFleetDistributionResponse(
                1, 0,
                Map.of(TrainStatus.DEPOT, 1L),
                Map.of(FleetRole.REGULAR_SERVICE, 1L),
                Map.of("9000", 1L)
        );
        DepotOperationResponse depot = new DepotOperationResponse(
                20L, "DEP-LF-A", "Cochera de Las Fuentes - Sector A", station,
                20, 4, 5, 1, 19, 5, DepotOperationStatus.AVAILABLE,
                fleet, movementSummary, List.of(movement)
        );
        when(queryService.getOperations()).thenReturn(new DepotOperationsResponse(
                evaluatedAt, ServiceOperationPhase.OPERATING,
                new DepotOperationsSummaryResponse(1, 20, 1, 19, 5, 1, 0, movementSummary),
                List.of(depot)
        ));

        mockMvc.perform(get("/api/depots/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.occupiedSpaces").value(1))
                .andExpect(jsonPath("$.summary.availableSpaces").value(19))
                .andExpect(jsonPath("$.depots[0].code").value("DEP-LF-A"))
                .andExpect(jsonPath("$.depots[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.depots[0].fleet.byRole.REGULAR_SERVICE").value(1))
                .andExpect(jsonPath("$.depots[0].movements[0].type").value("EXIT"))
                .andExpect(jsonPath("$.depots[0].movements[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.depots[0].movements[0].secondsUntilMovement").value(600));

        verify(queryService).getOperations();
    }
}
