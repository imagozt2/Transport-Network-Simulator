package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.repository.TicketProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TransportTitleQueryServiceTests {

    @Mock
    private TicketProductRepository ticketProductRepository;

    private TransportTitleQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new TransportTitleQueryService(ticketProductRepository);
    }

    @Test
    void shouldFilterTitlesAndKeepAnUnfilteredCatalogSummary() {
        TicketProduct singleTrip = title(
                1L, "SINGLE_TRIP", "Billete sencillo",
                TicketProductType.SINGLE_TRIP, true, true
        );
        TicketProduct timePass = mock(TicketProduct.class);
        when(timePass.getCode()).thenReturn("TIME_PASS");
        when(timePass.getName()).thenReturn("Abono temporal");
        when(timePass.getDescription()).thenReturn("Válido durante varios días");
        when(timePass.getProductType()).thenReturn(TicketProductType.TIME_PASS);
        when(timePass.isActive()).thenReturn(false);
        when(ticketProductRepository.findAllByOrderByCodeAsc())
                .thenReturn(List.of(singleTrip, timePass));

        var response = queryService.getTitles("sencillo", TicketProductType.SINGLE_TRIP, true, true);

        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.titles()).singleElement().satisfies(title -> {
            assertThat(title.code()).isEqualTo("SINGLE_TRIP");
            assertThat(title.type()).isEqualTo(TicketProductType.SINGLE_TRIP);
        });
        assertThat(response.summary().totalTitles()).isEqualTo(2);
        assertThat(response.summary().filteredTitles()).isEqualTo(1);
        assertThat(response.summary().activeTitles()).isEqualTo(1);
        assertThat(response.summary().inactiveTitles()).isEqualTo(1);
        assertThat(response.summary().byType())
                .containsEntry(TicketProductType.SINGLE_TRIP, 1L)
                .containsEntry(TicketProductType.TIME_PASS, 1L)
                .containsEntry(TicketProductType.MULTI_TRIP, 0L)
                .containsEntry(TicketProductType.SMART_BALANCE, 0L);
    }

    @Test
    void shouldFindTitlesByIdAndCaseInsensitiveCode() {
        TicketProduct product = title(
                4L, "SMART_BALANCE", "Saldo inteligente",
                TicketProductType.SMART_BALANCE, true, true
        );
        when(ticketProductRepository.findById(4L)).thenReturn(Optional.of(product));
        when(ticketProductRepository.findByCodeIgnoreCase("smart_balance"))
                .thenReturn(Optional.of(product));

        assertThat(queryService.getTitle(4L).code()).isEqualTo("SMART_BALANCE");
        assertThat(queryService.getTitle(" smart_balance ").id()).isEqualTo(4L);
    }

    @Test
    void shouldReturnNotFoundForAnUnknownTitle() {
        when(ticketProductRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getTitle(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private TicketProduct title(
            long id,
            String code,
            String name,
            TicketProductType type,
            boolean active,
            boolean rechargeable
    ) {
        TicketProduct product = mock(TicketProduct.class);
        when(product.getId()).thenReturn(id);
        when(product.getCode()).thenReturn(code);
        when(product.getName()).thenReturn(name);
        when(product.getDescription()).thenReturn("Descripción de " + name);
        when(product.getProductType()).thenReturn(type);
        when(product.getBasePrice()).thenReturn(new BigDecimal("0.50"));
        when(product.getPricePerStation()).thenReturn(new BigDecimal("0.05"));
        when(product.getPricePerTrip()).thenReturn(BigDecimal.ZERO);
        when(product.getPricePerDay()).thenReturn(BigDecimal.ZERO);
        when(product.isRechargeable()).thenReturn(rechargeable);
        when(product.isActive()).thenReturn(active);
        return product;
    }
}
