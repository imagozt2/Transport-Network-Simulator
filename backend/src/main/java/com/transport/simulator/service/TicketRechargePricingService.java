package com.transport.simulator.service;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.service.model.NetworkJourney;
import com.transport.simulator.service.model.TicketRechargeParameters;
import com.transport.simulator.service.model.TicketRechargeQuote;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class TicketRechargePricingService {

    private final NetworkJourneyPlanningService journeyPlanningService;

    public TicketRechargePricingService(NetworkJourneyPlanningService journeyPlanningService) {
        this.journeyPlanningService = journeyPlanningService;
    }

    public TicketRechargeQuote quote(Ticket ticket, TicketRechargeParameters parameters) {
        Objects.requireNonNull(ticket, "ticket is required");
        Objects.requireNonNull(parameters, "parameters are required");
        TicketProduct product = ticket.getProduct();

        return switch (ticket.getProductType()) {
            case SINGLE_TRIP -> quoteSingleTrip(ticket, product, parameters);
            case MULTI_TRIP -> quoteMultiTrip(ticket, product, parameters);
            case TIME_PASS -> quoteTimePass(ticket, product, parameters);
            case SMART_BALANCE -> quoteSmartBalance(ticket, product, parameters);
        };
    }

    private TicketRechargeQuote quoteSingleTrip(
            Ticket ticket, TicketProduct product, TicketRechargeParameters parameters
    ) {
        requireOnly(parameters, true, false, false, false);
        String origin = normalize(parameters.originStationCode(), "originStationCode");
        String destination = normalize(parameters.destinationStationCode(), "destinationStationCode");
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("Origin and destination stations must be different");
        }
        NetworkJourney journey = journeyPlanningService.calculate(origin, destination);
        BigDecimal total = product.getBasePrice().add(
                product.getPricePerStation().multiply(BigDecimal.valueOf(journey.stationCount()))
        );
        return quote(ticket, origin, destination, journey.stationCount(), null, null,
                null, null, null, total);
    }

    private TicketRechargeQuote quoteMultiTrip(
            Ticket ticket, TicketProduct product, TicketRechargeParameters parameters
    ) {
        requireOnly(parameters, false, true, false, false);
        int trips = requireRange(parameters.trips(), product.getMinTrips(), product.getMaxTrips(), "trips");
        int currentTrips = ticket.getRemainingTrips() == null ? 0 : ticket.getRemainingTrips();
        int resultingTrips = Math.addExact(currentTrips, trips);
        if (resultingTrips > product.getMaxTrips()) {
            throw new IllegalArgumentException("The resulting trip balance exceeds the product maximum");
        }
        BigDecimal total = product.getPricePerTrip().multiply(BigDecimal.valueOf(trips));
        return quote(ticket, null, null, null, trips, resultingTrips,
                null, null, null, total);
    }

    private TicketRechargeQuote quoteTimePass(
            Ticket ticket, TicketProduct product, TicketRechargeParameters parameters
    ) {
        requireOnly(parameters, false, false, true, false);
        int days = requireRange(parameters.days(), product.getMinDays(), product.getMaxDays(), "days");
        BigDecimal total = product.getPricePerDay().multiply(BigDecimal.valueOf(days));
        return quote(ticket, null, null, null, null, null, days, null, null, total);
    }

    private TicketRechargeQuote quoteSmartBalance(
            Ticket ticket, TicketProduct product, TicketRechargeParameters parameters
    ) {
        requireOnly(parameters, false, false, false, true);
        BigDecimal amount = Objects.requireNonNull(parameters.balanceAmount(), "balanceAmount is required");
        if (amount.compareTo(product.getMinRechargeAmount()) < 0
                || amount.compareTo(product.getMaxRechargeAmount()) > 0) {
            throw new IllegalArgumentException("Recharge amount is outside the product limits");
        }
        BigDecimal currentBalance = ticket.getBalanceAmount() == null
                ? BigDecimal.ZERO : ticket.getBalanceAmount();
        return quote(ticket, null, null, null, null, null, null, amount,
                currentBalance.add(amount), amount);
    }

    private TicketRechargeQuote quote(
            Ticket ticket,
            String origin,
            String destination,
            Integer stationCount,
            Integer trips,
            Integer resultingTrips,
            Integer days,
            BigDecimal balanceAmount,
            BigDecimal resultingBalance,
            BigDecimal total
    ) {
        return new TicketRechargeQuote(
                ticket.getCode(), ticket.getProductType(), origin, destination, stationCount,
                trips, resultingTrips, days, balanceAmount, resultingBalance, total,
                ticket.getCurrency()
        );
    }

    private void requireOnly(
            TicketRechargeParameters value,
            boolean route,
            boolean trips,
            boolean days,
            boolean balance
    ) {
        boolean hasOrigin = hasText(value.originStationCode());
        boolean hasDestination = hasText(value.destinationStationCode());
        boolean validRoute = route ? hasOrigin && hasDestination : !hasOrigin && !hasDestination;
        boolean valid = validRoute
                && (trips == (value.trips() != null))
                && (days == (value.days() != null))
                && (balance == (value.balanceAmount() != null));
        if (!valid) {
            throw new IllegalArgumentException("The recharge parameters do not match the ticket product");
        }
    }

    private int requireRange(Integer value, Integer minimum, Integer maximum, String field) {
        if (value == null || minimum == null || maximum == null
                || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    field + " must be between " + minimum + " and " + maximum
            );
        }
        return value;
    }

    private String normalize(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
