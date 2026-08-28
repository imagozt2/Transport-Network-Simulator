package com.transport.simulator.service;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.Purchase;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.DeviceMqttCommandType;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.PaymentMethod;
import com.transport.simulator.enums.TicketQrCredentialStatus;
import com.transport.simulator.mqtt.MqttDeviceCommandService;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.PurchaseRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketProductRepository;
import com.transport.simulator.repository.TicketQrCredentialRepository;
import com.transport.simulator.service.model.IssuedTicket;
import com.transport.simulator.service.model.NetworkJourney;
import com.transport.simulator.service.model.TicketIssuanceParameters;
import com.transport.simulator.service.model.TicketMachinePurchaseRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketMachinePurchaseService {
    private final DeviceRepository deviceRepository;
    private final TicketProductRepository productRepository;
    private final StationRepository stationRepository;
    private final PurchaseRepository purchaseRepository;
    private final TicketQrCredentialRepository qrCredentialRepository;
    private final NetworkJourneyPlanningService journeyPlanningService;
    private final TicketIssuanceService issuanceService;
    private final TicketQrImageService qrImageService;
    private final MqttDeviceCommandService commandService;
    private final Clock clock;

    public TicketMachinePurchaseService(DeviceRepository deviceRepository,
            TicketProductRepository productRepository, StationRepository stationRepository,
            PurchaseRepository purchaseRepository,
            TicketQrCredentialRepository qrCredentialRepository,
            NetworkJourneyPlanningService journeyPlanningService,
            TicketIssuanceService issuanceService, TicketQrImageService qrImageService,
            MqttDeviceCommandService commandService, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.productRepository = productRepository;
        this.stationRepository = stationRepository;
        this.purchaseRepository = purchaseRepository;
        this.qrCredentialRepository = qrCredentialRepository;
        this.journeyPlanningService = journeyPlanningService;
        this.issuanceService = issuanceService;
        this.qrImageService = qrImageService;
        this.commandService = commandService;
        this.clock = clock;
    }

    @Transactional
    public Purchase purchase(Long authenticatedDeviceId, TicketMachinePurchaseRequest request) {
        Purchase previous = purchaseRepository.findByExternalReference(request.purchaseReference()).orElse(null);
        if (previous != null) {
            return previous;
        }
        Device device = deviceRepository.findByIdForMqttUpdate(authenticatedDeviceId)
                .filter(candidate -> candidate.getType() == DeviceType.TICKET_MACHINE)
                .orElseThrow(() -> new IllegalArgumentException("Active ticket machine not found"));
        TicketProduct product = productRepository.findByCodeIgnoreCase(normalize(request.productCode()))
                .filter(TicketProduct::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Active ticket product not found"));
        Definition definition = definition(product, request);
        BigDecimal paid = money(request.paidAmount());
        if (definition.amount().compareTo(paid) != 0) {
            throw new IllegalArgumentException("Paid amount does not match the authoritative fare");
        }

        String linkingCode = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
        IssuedTicket issued = issuanceService.issuePhysical(
                product,
                definition.parameters(),
                device,
                "RMM-PHY-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT),
                linkingCode
        );
        Purchase purchase = Purchase.completedMachinePurchase(
                uniqueCode("RMM-PUR"), issued.ticket(), PaymentMethod.SIMULATED,
                request.purchaseReference(), device, paid, LocalDateTime.now(clock));
        configure(purchase, product, definition);
        Purchase persisted = purchaseRepository.save(purchase);
        var credential = qrCredentialRepository
                .findFirstByTicketIdAndStatusOrderByIssuedAtDesc(
                        issued.ticket().getId(), TicketQrCredentialStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Issued ticket has no active QR credential"));

        Map<String, Object> ticket = new LinkedHashMap<>();
        ticket.put("ticketCode", issued.ticket().getCode());
        ticket.put("productType", product.getProductType().name());
        ticket.put("qrValue", credential.getQrValue());
        ticket.put("qrPngBase64", qrImageService.pngBase64(credential.getQrValue()));
        ticket.put("linkingCode", linkingCode);
        if (definition.origin() != null) {
            ticket.put("originStationCode", definition.origin().getCode());
            ticket.put("destinationStationCode", definition.destination().getCode());
        }
        commandService.send(device.getCode(), DeviceMqttCommandType.TICKET_ISSUE,
                Map.of(
                        "issuanceKind", "PURCHASE",
                        "purchaseReference", request.purchaseReference(),
                        "issuanceCode", persisted.getCode(),
                        "ticket", ticket
                ), Duration.ofMinutes(2));
        return persisted;
    }

    private Definition definition(TicketProduct product, TicketMachinePurchaseRequest request) {
        return switch (product.getProductType()) {
            case SINGLE_TRIP -> singleTrip(product, request);
            case MULTI_TRIP -> new Definition(
                    TicketIssuanceParameters.multiTrip(requiredQuantity(request)),
                    product.getPricePerTrip().multiply(BigDecimal.valueOf(requiredQuantity(request))), null, null);
            case TIME_PASS -> new Definition(
                    TicketIssuanceParameters.timePass(requiredQuantity(request)),
                    product.getPricePerDay().multiply(BigDecimal.valueOf(requiredQuantity(request))), null, null);
            case SMART_BALANCE -> new Definition(
                    TicketIssuanceParameters.smartBalance(request.rechargeAmount()),
                    money(request.rechargeAmount()), null, null);
        };
    }

    private Definition singleTrip(TicketProduct product, TicketMachinePurchaseRequest request) {
        Station origin = station(request.originStationCode());
        Station destination = station(request.destinationStationCode());
        if (origin.getId().equals(destination.getId())) {
            throw new IllegalArgumentException("Origin and destination must be different");
        }
        NetworkJourney journey = journeyPlanningService.calculate(origin.getCode(), destination.getCode());
        BigDecimal amount = product.getBasePrice().add(
                product.getPricePerStation().multiply(BigDecimal.valueOf(journey.stationCount())));
        return new Definition(
                TicketIssuanceParameters.singleTrip(origin, destination, journey.stationCount()),
                amount, origin, destination);
    }

    private void configure(Purchase purchase, TicketProduct product, Definition definition) {
        switch (product.getProductType()) {
            case SINGLE_TRIP -> purchase.configureSingleTrip(
                    definition.origin(), definition.destination(), definition.parameters().stationCount());
            case MULTI_TRIP -> purchase.configureTrips(definition.parameters().trips());
            case TIME_PASS -> purchase.configureDays(definition.parameters().days());
            case SMART_BALANCE -> purchase.configureMoney(definition.parameters().balanceAmount());
        }
    }

    private int requiredQuantity(TicketMachinePurchaseRequest request) {
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("A positive quantity is required");
        }
        return request.quantity();
    }

    private Station station(String code) {
        return stationRepository.findByCodeAndActiveTrue(normalize(code))
                .orElseThrow(() -> new IllegalArgumentException("Active station not found"));
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("A positive amount is required");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A code is required");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }

    private record Definition(TicketIssuanceParameters parameters, BigDecimal rawAmount,
            Station origin, Station destination) {
        private BigDecimal amount() { return rawAmount.setScale(2, RoundingMode.HALF_UP); }
    }
}
