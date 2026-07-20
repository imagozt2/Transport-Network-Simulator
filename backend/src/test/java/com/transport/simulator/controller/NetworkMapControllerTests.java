package com.transport.simulator.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transport.simulator.dto.response.networkmap.NetworkMapLineResponse;
import com.transport.simulator.dto.response.networkmap.NetworkMapResponse;
import com.transport.simulator.dto.response.networkmap.NetworkMapStationResponse;
import com.transport.simulator.service.NetworkMapQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class NetworkMapControllerTests {

    @Mock
    private NetworkMapQueryService networkMapQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NetworkMapController(networkMapQueryService))
                .build();
    }

    @Test
    void shouldExposeTheCompleteNetworkMapContract() throws Exception {
        NetworkMapStationResponse station = new NetworkMapStationResponse(
                45L,
                "ST045",
                "Los Molinos",
                1
        );
        NetworkMapLineResponse line = new NetworkMapLineResponse(
                1L,
                "L1",
                "Línea 1",
                "Roja",
                List.of(station)
        );
        when(networkMapQueryService.getNetworkMap())
                .thenReturn(new NetworkMapResponse(List.of(line)));

        mockMvc.perform(get("/api/network-map"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].id").value(1))
                .andExpect(jsonPath("$.lines[0].code").value("L1"))
                .andExpect(jsonPath("$.lines[0].name").value("Línea 1"))
                .andExpect(jsonPath("$.lines[0].color").value("Roja"))
                .andExpect(jsonPath("$.lines[0].stations.length()").value(1))
                .andExpect(jsonPath("$.lines[0].stations[0].id").value(45))
                .andExpect(jsonPath("$.lines[0].stations[0].code").value("ST045"))
                .andExpect(jsonPath("$.lines[0].stations[0].name").value("Los Molinos"))
                .andExpect(jsonPath("$.lines[0].stations[0].stationOrder").value(1));

        verify(networkMapQueryService).getNetworkMap();
    }

    @Test
    void shouldReturnAnEmptyLinesArrayWhenTheNetworkHasNoActiveLines() throws Exception {
        when(networkMapQueryService.getNetworkMap())
                .thenReturn(new NetworkMapResponse(List.of()));

        mockMvc.perform(get("/api/network-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").isArray())
                .andExpect(jsonPath("$.lines").isEmpty());
    }
}
