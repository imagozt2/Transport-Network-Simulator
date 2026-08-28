package com.transport.simulator.service;

import com.transport.simulator.dto.request.passengerticket.PassengerTicketConfigurationRequest;
import com.transport.simulator.dto.request.passengerticket.PassengerTicketPurchaseRequest;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketPurchaseResponse;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.Purchase;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.PaymentMethod;
import com.transport.simulator.repository.PurchaseRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketProductRepository;
import com.transport.simulator.service.model.IssuedTicket;
import com.transport.simulator.service.model.NetworkJourney;
import com.transport.simulator.service.model.TicketIssuanceParameters;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PassengerTicketPurchaseService {

    private final PassengerResourceAccessService accessService;
    private final TicketProductRepository productRepository;
    private final StationRepository stationRepository;
    private final PurchaseRepository purchaseRepository;
    private final NetworkJourneyPlanningService journeyPlanningService;
    private final TicketIssuanceService issuanceService;
    private final Clock clock;

    public PassengerTicketPurchaseService(
            PassengerResourceAccessService accessService,
            TicketProductRepository productRepository,
            StationRepository stationRepository,
            PurchaseRepository purchaseRepository,
            NetworkJourneyPlanningService journeyPlanningService,
            TicketIssuanceService issuanceService,
            Clock clock
    ) {
        this.accessService = accessService;
        this.productRepository = productRepository;
        this.stationRepository = stationRepository;
        this.purchaseRepository = purchaseRepository;
        this.journeyPlanningService = journeyPlanningService;
        this.issuanceService = issuanceService;
        this.clock = clock;
    }

    @Transactional
    public Purchase purchase(
            String idempotencyKey,
            PassengerTicketPurchaseRequest request,
            Authentication authentication
    ) {
        PassengerAccount passenger = accessService.currentAccount(authentication);
        String reference = requireIdempotencyKey(idempotencyKey);
        Purchase existing = purchaseRepository.findByExternalReference(reference).orElse(null);
        if (existing != null) {
            if (existing.getPassengerAccount() == null
                    || !existing.getPassengerAccount().getId().equals(passenger.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key is already in use");
            }
            return existing;
        }

        TicketProduct product = productRepository.findByCodeIgnoreCase(normalize(request.productCode()))
                .filter(TicketProduct::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Active ticket product not found"
                ));
        if (request.paymentMethod() != PaymentMethod.SIMULATED
                && request.paymentMethod() != PaymentMethod.CARD) {
            throw badRequest("RMM App only accepts simulated or card payments");
        }

        PurchaseDefinition definition = define(product, request.configuration());
        IssuedTicket issued = issuanceService.issueDigital(product, definition.parameters(), passenger);
        LocalDateTime now = LocalDateTime.now(clock);
        Purchase purchase = Purchase.completedPurchase(
                uniqueCode("RMM-PUR"), issued.ticket(), request.paymentMethod(), reference,
                passenger, definition.amount(), now
        );
        applyConfiguration(purchase, product, definition);
        return purchaseRepository.save(purchase);
    }

    @Transactional(readOnly = true)
    public Purchase ownedPurchase(String code, Authentication authentication) {
        return accessService.ownedPurchase(code, authentication);
    }

    private PurchaseDefinition define(
            TicketProduct product,
            PassengerTicketConfigurationRequest configuration
    ) {
        requireCompatibleConfiguration(product, configuration);
        return switch (product.getProductType()) {
            case SINGLE_TRIP -> singleTrip(product, configuration);
            case MULTI_TRIP -> new PurchaseDefinition(
                    TicketIssuanceParameters.multiTrip(configuration.tripCount()),
                    product.getPricePerTrip().multiply(decimal(configuration.tripCount())), null, null
            );
            case TIME_PASS -> new PurchaseDefinition(
                    TicketIssuanceParameters.timePass(configuration.dayCount()),
                    product.getPricePerDay().multiply(decimal(configuration.dayCount())), null, null
            );
            case SMART_BALANCE -> new PurchaseDefinition(
                    TicketIssuanceParameters.smartBalance(configuration.rechargeAmount()),
                    configuration.rechargeAmount(), null, null
            );
        };
    }

    private void requireCompatibleConfiguration(
            TicketProduct product,
            PassengerTicketConfigurationRequest configuration
    ) {
        boolean valid = switch (product.getProductType()) {
            case SINGLE_TRIP -> configuration.originStationCode() != null
                    && configuration.destinationStationCode() != null
                    && configuration.tripCount() == null && configuration.dayCount() == null
                    && configuration.rechargeAmount() == null;
            case MULTI_TRIP -> configuration.originStationCode() == null
                    && configuration.destinationStationCode() == null
                    && configuration.tripCount() != null && configuration.dayCount() == null
                    && configuration.rechargeAmount() == null;
            case TIME_PASS -> configuration.originStationCode() == null
                    && configuration.destinationStationCode() == null
                    && configuration.tripCount() == null && configuration.dayCount() != null
                    && configuration.rechargeAmount() == null;
            case SMART_BALANCE -> configuration.originStationCode() == null
                    && configuration.destinationStationCode() == null
                    && configuration.tripCount() == null && configuration.dayCount() == null
                    && configuration.rechargeAmount() != null;
        };
        if (!valid) {
            throw badRequest("Configuration does not match the selected ticket product");
        }
    }

    private PurchaseDefinition singleTrip(
            TicketProduct product,
            PassengerTicketConfigurationRequest configuration
    ) {
        Station origin = requiredStation(configuration.originStationCode());
        Station destination = requiredStation(configuration.destinationStationCode());
        if (origin.getId().equals(destination.getId())) {
            throw badRequest("Origin and destination stations must be different");
        }
        NetworkJourney journey = journeyPlanningService.calculate(origin.getCode(), destination.getCode());
        BigDecimal amount = product.getBasePrice().add(
                product.getPricePerStation().multiply(decimal(journey.stationCount()))
        );
        return new PurchaseDefinition(
                TicketIssuanceParameters.singleTrip(origin, destination, journey.stationCount()),
                amount, origin, destination
        );
    }

    private void applyConfiguration(
            Purchase purchase,
            TicketProduct product,
            PurchaseDefinition definition
    ) {
        switch (product.getProductType()) {
            case SINGLE_TRIP -> purchase.configureSingleTrip(
                    definition.origin(), definition.destination(), definition.parameters().stationCount()
            );
            case MULTI_TRIP -> purchase.configureTrips(definition.parameters().trips());
            case TIME_PASS -> purchase.configureDays(definition.parameters().days());
            case SMART_BALANCE -> purchase.configureMoney(definition.parameters().balanceAmount());
        }
    }

    private Station requiredStation(String code) {
        return stationRepository.findByCodeAndActiveTrue(normalize(code))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active station not found"));
    }

    private BigDecimal decimal(Integer value) {
        if (value == null) {
            throw badRequest("The selected product configuration is incomplete");
        }
        return BigDecimal.valueOf(value);
    }

    private String requireIdempotencyKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.length() < 16 || key.length() > 150) {
            throw badRequest("Idempotency-Key must contain between 16 and 150 characters");
        }
        return key;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw badRequest("A code is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record PurchaseDefinition(
            TicketIssuanceParameters parameters,
            BigDecimal rawAmount,
            Station origin,
            Station destination
    ) {
        private BigDecimal amount() {
            if (rawAmount == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase amount is required");
            }
            return rawAmount.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
