package com.transport.simulator.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.enums.TicketStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TicketLifecycleTests {

    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 8, 8, 9, 0);

    @Test
    void shouldCalculateExhaustAndRechargeASingleTrip() {
        Ticket ticket = ticket(product(TicketProductType.SINGLE_TRIP));
        Station origin = station("ST001");
        Station destination = station("ST005");

        ticket.configureSingleTrip(origin, destination, 5);

        assertThat(ticket.getRoutePriceAmount()).isEqualByComparingTo("0.75");
        ticket.exhaust(ISSUED_AT.plusMinutes(20));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EXHAUSTED);

        ticket.rechargeSingleTrip(destination, origin, 5, ISSUED_AT.plusHours(1));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ACTIVE);
        assertThat(ticket.getOriginStation()).isSameAs(destination);
        assertThat(ticket.getDestinationStation()).isSameAs(origin);
    }

    @Test
    void shouldConsumeAndRechargeAMultiTripBalance() {
        Ticket ticket = ticket(product(TicketProductType.MULTI_TRIP));
        ticket.configureTripBalance(2);

        ticket.consumeTrip(ISSUED_AT.plusMinutes(1));
        ticket.consumeTrip(ISSUED_AT.plusMinutes(2));

        assertThat(ticket.getRemainingTrips()).isZero();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EXHAUSTED);

        ticket.rechargeTrips(3, ISSUED_AT.plusMinutes(3));

        assertThat(ticket.getRemainingTrips()).isEqualTo(3);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ACTIVE);
    }

    @Test
    void shouldRejectAMultiTripRechargeThatExceedsTheProductLimit() {
        Ticket ticket = ticket(product(TicketProductType.MULTI_TRIP));
        ticket.configureTripBalance(29);

        assertThatThrownBy(() -> ticket.rechargeTrips(2, ISSUED_AT.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the product maximum");
    }

    @Test
    void shouldExpireAndRenewATimePass() {
        Ticket ticket = ticket(product(TicketProductType.TIME_PASS));
        ticket.configureValidity(2, ISSUED_AT);

        ticket.refreshTimePassStatus(ISSUED_AT.plusDays(2).plusSeconds(1));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EXPIRED);

        LocalDateTime renewedAt = ISSUED_AT.plusDays(3);
        ticket.renewValidity(5, renewedAt);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ACTIVE);
        assertThat(ticket.getValidFrom()).isEqualTo(renewedAt);
        assertThat(ticket.getValidUntil()).isEqualTo(renewedAt.plusDays(5));
    }

    @Test
    void shouldDeductAndRechargeSmartBalance() {
        Ticket ticket = ticket(product(TicketProductType.SMART_BALANCE));
        ticket.configureMoneyBalance(new BigDecimal("1.00"));
        BigDecimal fare = ticket.calculateSmartBalanceFare(5);

        ticket.deductSmartBalanceFare(fare, ISSUED_AT.plusMinutes(1));

        assertThat(fare).isEqualByComparingTo("0.50");
        assertThat(ticket.getBalanceAmount()).isEqualByComparingTo("0.50");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ACTIVE);

        ticket.deductSmartBalanceFare(new BigDecimal("0.45"), ISSUED_AT.plusMinutes(2));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EXHAUSTED);

        ticket.rechargeMoneyBalance(new BigDecimal("10.00"), ISSUED_AT.plusMinutes(3));
        assertThat(ticket.getBalanceAmount()).isEqualByComparingTo("10.05");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ACTIVE);
    }

    @Test
    void shouldRejectConfigurationFromAnotherProductType() {
        Ticket ticket = ticket(product(TicketProductType.SINGLE_TRIP));

        assertThatThrownBy(() -> ticket.configureTripBalance(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only multi-trip tickets");
        assertThatThrownBy(() -> ticket.configureValidity(5, ISSUED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only time passes");
        assertThatThrownBy(() -> ticket.configureMoneyBalance(BigDecimal.TEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only smart-balance tickets");
    }

    private Ticket ticket(TicketProduct product) {
        return new Ticket("RMM-TKT-001", "qr-token", product, ISSUED_AT);
    }

    private TicketProduct product(TicketProductType type) {
        TicketProduct product = mock(TicketProduct.class);
        when(product.getProductType()).thenReturn(type);
        when(product.getBasePrice()).thenReturn(
                type == TicketProductType.SMART_BALANCE
                        ? new BigDecimal("0.25")
                        : new BigDecimal("0.50")
        );
        when(product.getPricePerStation()).thenReturn(new BigDecimal("0.05"));
        when(product.getPricePerTrip()).thenReturn(BigDecimal.ONE);
        when(product.getPricePerDay()).thenReturn(new BigDecimal("2.00"));
        when(product.getMinTrips()).thenReturn(2);
        when(product.getMaxTrips()).thenReturn(30);
        when(product.getMinDays()).thenReturn(2);
        when(product.getMaxDays()).thenReturn(30);
        when(product.getMinRechargeAmount()).thenReturn(BigDecimal.ONE);
        when(product.getMaxRechargeAmount()).thenReturn(new BigDecimal("100.00"));
        return product;
    }

    private Station station(String code) {
        Station station = mock(Station.class);
        when(station.getCode()).thenReturn(code);
        return station;
    }
}
