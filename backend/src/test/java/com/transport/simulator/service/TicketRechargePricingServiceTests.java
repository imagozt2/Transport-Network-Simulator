package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.service.model.NetworkJourney;
import com.transport.simulator.service.model.TicketRechargeParameters;
import com.transport.simulator.service.model.TicketRechargeQuote;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketRechargePricingServiceTests {

    @Mock private NetworkJourneyPlanningService journeyPlanningService;
    @Mock private Ticket ticket;
    @Mock private TicketProduct product;

    private TicketRechargePricingService service;

    @BeforeEach
    void setUp() {
        service = new TicketRechargePricingService(journeyPlanningService);
        when(ticket.getProduct()).thenReturn(product);
        when(ticket.getCode()).thenReturn("RMM-TKT-001");
        when(ticket.getCurrency()).thenReturn("EUR");
    }

    @Test
    void shouldCalculateASingleTripFromTheActualNetworkRoute() {
        prepareType(TicketProductType.SINGLE_TRIP);
        when(product.getBasePrice()).thenReturn(new BigDecimal("0.50"));
        when(product.getPricePerStation()).thenReturn(new BigDecimal("0.05"));
        when(journeyPlanningService.calculate("ST001", "ST010")).thenReturn(new NetworkJourney(
                new NetworkJourney.Station(1L, "ST001", "Aeropuerto"),
                new NetworkJourney.Station(10L, "ST010", "Gueto Norte"),
                9, 0, 600, List.of(), List.of()
        ));

        TicketRechargeQuote result = service.quote(
                ticket, TicketRechargeParameters.singleTrip("st001", "st010")
        );

        assertThat(result.stationCount()).isEqualTo(9);
        assertThat(result.totalAmount()).isEqualByComparingTo("0.95");
        assertThat(result.originStationCode()).isEqualTo("ST001");
        assertThat(result.destinationStationCode()).isEqualTo("ST010");
    }

    @Test
    void shouldCalculateTripsAndRespectTheResultingBalanceLimit() {
        prepareType(TicketProductType.MULTI_TRIP);
        when(product.getMinTrips()).thenReturn(2);
        when(product.getMaxTrips()).thenReturn(30);
        when(product.getPricePerTrip()).thenReturn(new BigDecimal("1.00"));
        when(ticket.getRemainingTrips()).thenReturn(24);

        TicketRechargeQuote result = service.quote(ticket, TicketRechargeParameters.multiTrip(6));

        assertThat(result.resultingTrips()).isEqualTo(30);
        assertThat(result.totalAmount()).isEqualByComparingTo("6.00");
        assertThatThrownBy(() -> service.quote(ticket, TicketRechargeParameters.multiTrip(7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void shouldCalculateTheDurationOfATimePass() {
        prepareType(TicketProductType.TIME_PASS);
        when(product.getMinDays()).thenReturn(2);
        when(product.getMaxDays()).thenReturn(30);
        when(product.getPricePerDay()).thenReturn(new BigDecimal("2.00"));

        TicketRechargeQuote result = service.quote(ticket, TicketRechargeParameters.timePass(7));

        assertThat(result.days()).isEqualTo(7);
        assertThat(result.totalAmount()).isEqualByComparingTo("14.00");
    }

    @Test
    void shouldCalculateTheResultingSmartBalance() {
        prepareType(TicketProductType.SMART_BALANCE);
        when(product.getMinRechargeAmount()).thenReturn(new BigDecimal("1.00"));
        when(product.getMaxRechargeAmount()).thenReturn(new BigDecimal("100.00"));
        when(ticket.getBalanceAmount()).thenReturn(new BigDecimal("3.25"));

        TicketRechargeQuote result = service.quote(
                ticket, TicketRechargeParameters.smartBalance(new BigDecimal("10.00"))
        );

        assertThat(result.balanceAmount()).isEqualByComparingTo("10.00");
        assertThat(result.resultingBalanceAmount()).isEqualByComparingTo("13.25");
        assertThat(result.totalAmount()).isEqualByComparingTo("10.00");
    }

    private void prepareType(TicketProductType type) {
        when(ticket.getProductType()).thenReturn(type);
    }
}
