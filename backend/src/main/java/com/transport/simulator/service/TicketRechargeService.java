package com.transport.simulator.service;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.Purchase;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.enums.PaymentMethod;
import com.transport.simulator.enums.PurchaseOrigin;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.repository.PurchaseRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.service.model.TicketRechargeParameters;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketRechargeService {

    private final TicketRepository ticketRepository;
    private final PurchaseRepository purchaseRepository;
    private final SingleTripTicketService singleTripService;
    private final MultiTripTicketService multiTripService;
    private final TimePassTicketService timePassService;
    private final SmartBalanceTicketService smartBalanceService;
    private final Clock clock;

    public TicketRechargeService(
            TicketRepository ticketRepository,
            PurchaseRepository purchaseRepository,
            SingleTripTicketService singleTripService,
            MultiTripTicketService multiTripService,
            TimePassTicketService timePassService,
            SmartBalanceTicketService smartBalanceService,
            Clock clock
    ) {
        this.ticketRepository = ticketRepository;
        this.purchaseRepository = purchaseRepository;
        this.singleTripService = singleTripService;
        this.multiTripService = multiTripService;
        this.timePassService = timePassService;
        this.smartBalanceService = smartBalanceService;
        this.clock = clock;
    }

    @Transactional
    public Purchase recharge(
            String ticketCode,
            TicketRechargeParameters parameters,
            PurchaseOrigin origin,
            PaymentMethod paymentMethod,
            String externalReference,
            Device device,
            PassengerAccount passenger
    ) {
        String normalizedTicketCode = normalize(ticketCode);
        String reference = requireText(externalReference, "externalReference");
        if (reference.length() > 150) {
            throw new IllegalArgumentException("externalReference cannot exceed 150 characters");
        }
        Ticket current = ticketRepository.findByCodeForUpdate(normalizedTicketCode)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        Purchase previous = purchaseRepository.findByExternalReference(reference).orElse(null);
        if (previous != null) {
            if (!previous.getTicket().getCode().equals(normalizedTicketCode)) {
                throw new IllegalArgumentException("The external reference belongs to another ticket");
            }
            return previous;
        }

        if (!current.getProduct().isRechargeable()) {
            throw new IllegalStateException("The ticket product is not rechargeable");
        }
        Objects.requireNonNull(parameters, "parameters are required");
        validateContext(origin, device, passenger, current);

        Ticket updated = switch (current.getProductType()) {
            case SINGLE_TRIP -> rechargeSingleTrip(current, parameters);
            case MULTI_TRIP -> rechargeMultiTrip(current, parameters);
            case TIME_PASS -> rechargeTimePass(current, parameters);
            case SMART_BALANCE -> rechargeSmartBalance(current, parameters);
        };

        BigDecimal total = calculatePrice(updated, parameters);
        Purchase purchase = Purchase.completedRecharge(
                uniqueCode("RMM-RCH"), updated, origin, paymentMethod, reference,
                device, passenger, total, LocalDateTime.now(clock)
        );
        configurePurchase(purchase, updated, parameters);
        return purchaseRepository.save(purchase);
    }

    private Ticket rechargeSingleTrip(Ticket ticket, TicketRechargeParameters parameters) {
        requireOnly(parameters, true, false, false, false);
        return singleTripService.recharge(
                ticket.getCode(), parameters.originStationCode(), parameters.destinationStationCode()
        );
    }

    private Ticket rechargeMultiTrip(Ticket ticket, TicketRechargeParameters parameters) {
        requireOnly(parameters, false, true, false, false);
        return multiTripService.recharge(ticket.getCode(), requirePositive(parameters.trips(), "trips"));
    }

    private Ticket rechargeTimePass(Ticket ticket, TicketRechargeParameters parameters) {
        requireOnly(parameters, false, false, true, false);
        return timePassService.renew(ticket.getCode(), requirePositive(parameters.days(), "days"));
    }

    private Ticket rechargeSmartBalance(Ticket ticket, TicketRechargeParameters parameters) {
        requireOnly(parameters, false, false, false, true);
        return smartBalanceService.recharge(
                ticket.getCode(), Objects.requireNonNull(parameters.balanceAmount(), "balanceAmount is required")
        );
    }

    private BigDecimal calculatePrice(Ticket ticket, TicketRechargeParameters parameters) {
        return switch (ticket.getProductType()) {
            case SINGLE_TRIP -> ticket.getRoutePriceAmount();
            case MULTI_TRIP -> ticket.getProduct().getPricePerTrip()
                    .multiply(BigDecimal.valueOf(parameters.trips()));
            case TIME_PASS -> ticket.getProduct().getPricePerDay()
                    .multiply(BigDecimal.valueOf(parameters.days()));
            case SMART_BALANCE -> parameters.balanceAmount();
        };
    }

    private void configurePurchase(
            Purchase purchase,
            Ticket ticket,
            TicketRechargeParameters parameters
    ) {
        switch (ticket.getProductType()) {
            case SINGLE_TRIP -> purchase.configureSingleTrip(
                    ticket.getOriginStation(), ticket.getDestinationStation(), ticket.getStationCount()
            );
            case MULTI_TRIP -> purchase.configureTrips(parameters.trips());
            case TIME_PASS -> purchase.configureDays(parameters.days());
            case SMART_BALANCE -> purchase.configureMoney(parameters.balanceAmount());
        }
    }

    private void validateContext(
            PurchaseOrigin origin,
            Device device,
            PassengerAccount passenger,
            Ticket ticket
    ) {
        Objects.requireNonNull(origin, "origin is required");
        if (origin == PurchaseOrigin.TICKET_MACHINE
                && (device == null || !device.isActive() || device.getType() != DeviceType.TICKET_MACHINE
                || device.getStatus() != DeviceStatus.ONLINE)) {
            throw new IllegalArgumentException("A ticket-machine recharge requires an active online device");
        }
        if (origin == PurchaseOrigin.RMM_APP
                && (passenger == null || passenger.getStatus() != PassengerAccountStatus.ACTIVE
                || ticket.getPassengerAccount() == null
                || !ticket.getPassengerAccount().getPublicId().equals(passenger.getPublicId()))) {
            throw new IllegalArgumentException("RMM App can only recharge a ticket owned by its active passenger");
        }
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

    private int requirePositive(Integer value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return requireText(value, "ticketCode").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }
}
