package com.transport.simulator.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transport.simulator.dto.response.transporttitle.TransportTitleResponse;
import com.transport.simulator.dto.response.transporttitle.TransportTitleSummaryResponse;
import com.transport.simulator.dto.response.transporttitle.TransportTitlesResponse;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.service.TransportTitleQueryService;
import java.math.BigDecimal;
import java.util.EnumMap;
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
class TransportTitleControllerTests {

    @Mock
    private TransportTitleQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TransportTitleController(queryService)).build();
    }

    @Test
    void shouldExposeTheFilteredTransportTitleCatalog() throws Exception {
        TransportTitleResponse title = response();
        Map<TicketProductType, Long> byType = new EnumMap<>(TicketProductType.class);
        for (TicketProductType type : TicketProductType.values()) {
            byType.put(type, type == TicketProductType.SINGLE_TRIP ? 1L : 0L);
        }
        when(queryService.getTitles("sencillo", TicketProductType.SINGLE_TRIP, true, true))
                .thenReturn(new TransportTitlesResponse(
                        "EUR",
                        new TransportTitleSummaryResponse(4, 1, 4, 0, byType),
                        List.of(title)
                ));

        mockMvc.perform(get("/api/transport-titles")
                        .param("search", "sencillo")
                        .param("type", "SINGLE_TRIP")
                        .param("active", "true")
                        .param("rechargeable", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.summary.totalTitles").value(4))
                .andExpect(jsonPath("$.summary.filteredTitles").value(1))
                .andExpect(jsonPath("$.titles[0].code").value("SINGLE_TRIP"))
                .andExpect(jsonPath("$.titles[0].type").value("SINGLE_TRIP"))
                .andExpect(jsonPath("$.titles[0].basePrice").value(0.50))
                .andExpect(jsonPath("$.titles[0].requiresOriginDestination").value(true));

        verify(queryService).getTitles("sencillo", TicketProductType.SINGLE_TRIP, true, true);
    }

    @Test
    void shouldExposeIndividualQueriesByIdAndCode() throws Exception {
        when(queryService.getTitle(1L)).thenReturn(response());
        when(queryService.getTitle("SINGLE_TRIP")).thenReturn(response());

        mockMvc.perform(get("/api/transport-titles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        mockMvc.perform(get("/api/transport-titles/code/SINGLE_TRIP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SINGLE_TRIP"));
    }

    private TransportTitleResponse response() {
        return new TransportTitleResponse(
                1L, "SINGLE_TRIP", "Billete sencillo", "Trayecto entre dos estaciones",
                TicketProductType.SINGLE_TRIP,
                new BigDecimal("0.50"), new BigDecimal("0.05"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, null,
                true, false, false, false, true, true, null, null
        );
    }
}
