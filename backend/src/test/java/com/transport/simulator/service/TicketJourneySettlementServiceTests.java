package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.service.model.NetworkJourney;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TicketJourneySettlementServiceTests {

    private final NetworkJourneyPlanningService planner = mock(NetworkJourneyPlanningService.class);
    private final TicketJourneySettlementService service = new TicketJourneySettlementService(planner);

    @Test
    void calculatesTheConfiguredSingleTripFareForTheRealRouteLength() {
        Ticket ticket = ticket(TicketProductType.SINGLE_TRIP);
        when(ticket.getRoutePriceAmount()).thenReturn(new BigDecimal("0.75"));
        route("ST001", "ST005", 5);

        var result = service.calculate(ticket, station("ST001"), station("ST005"));

        assertThat(result.stationCount()).isEqualTo(5);
        assertThat(result.fareAmount()).isEqualByComparingTo("0.75");
    }

    @Test
    void appliesOneTripToAMultiTripAndNoExtraFareToATimePass() {
        Ticket multiTrip = ticket(TicketProductType.MULTI_TRIP);
        TicketProduct product = mock(TicketProduct.class);
        when(multiTrip.getProduct()).thenReturn(product);
        when(product.getPricePerTrip()).thenReturn(new BigDecimal("1.00"));
        route("ST001", "ST004", 4);

        var multiTripResult = service.calculate(
                multiTrip, station("ST001"), station("ST004"));

        Ticket timePass = ticket(TicketProductType.TIME_PASS);
        var timePassResult = service.calculate(
                timePass, station("ST010"), station("ST010"));

        assertThat(multiTripResult.stationCount()).isEqualTo(4);
        assertThat(multiTripResult.fareAmount()).isEqualByComparingTo("1.00");
        assertThat(timePassResult.stationCount()).isOne();
        assertThat(timePassResult.fareAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void calculatesSmartBalanceFromTheStationsActuallyTravelled() {
        Ticket ticket = ticket(TicketProductType.SMART_BALANCE);
        when(ticket.calculateSmartBalanceFare(7)).thenReturn(new BigDecimal("0.600"));
        route("ST001", "ST007", 7);

        var result = service.calculate(ticket, station("ST001"), station("ST007"));

        assertThat(result.stationCount()).isEqualTo(7);
        assertThat(result.fareAmount()).isEqualByComparingTo("0.60");
    }

    @Test
    void sameStationCountsAsOneWithoutCallingThePlanner() {
        Ticket ticket = ticket(TicketProductType.TIME_PASS);

        var result = service.calculate(ticket, station("ST001"), station("ST001"));

        assertThat(result.stationCount()).isOne();
        verifyNoInteractions(planner);
    }

    @Test
    void rejectsATicketWithoutTheFareRequiredByItsProduct() {
        Ticket ticket = ticket(TicketProductType.SINGLE_TRIP);
        route("ST001", "ST002", 2);

        assertThatThrownBy(() -> service.calculate(
                ticket, station("ST001"), station("ST002")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not define a journey fare");
    }

    private Ticket ticket(TicketProductType type) {
        Ticket ticket = mock(Ticket.class);
        when(ticket.getProductType()).thenReturn(type);
        return ticket;
    }

    private Station station(String code) {
        Station station = mock(Station.class);
        when(station.getCode()).thenReturn(code);
        return station;
    }

    private void route(String origin, String destination, int stationCount) {
        var first = new NetworkJourney.Station(1L, origin, origin);
        var last = new NetworkJourney.Station(2L, destination, destination);
        when(planner.calculate(origin, destination)).thenReturn(new NetworkJourney(
                first, last, stationCount, 0, 600, List.of(first, last), List.of()
        ));
    }
}
