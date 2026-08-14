package com.transport.simulator.service;

import com.transport.simulator.dto.request.transporttitle.CompensatoryTicketIssuanceRequest;
import com.transport.simulator.dto.response.transporttitle.CompensatoryTicketIssuanceResponse;
import com.transport.simulator.entity.CompensatoryTicketIssuance;
import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.DeviceMqttCommandType;
import com.transport.simulator.enums.CompensatoryDeliveryMethod;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.repository.CompensatoryTicketIssuanceRepository;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.OperatorAccountRepository;
import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketProductRepository;
import com.transport.simulator.repository.TicketQrCredentialRepository;
import com.transport.simulator.enums.TicketQrCredentialStatus;
import com.transport.simulator.mqtt.MqttDeviceCommandService;
import com.transport.simulator.security.OperatorPrincipal;
import com.transport.simulator.service.model.NetworkJourney;
import com.transport.simulator.service.model.IssuedTicket;
import com.transport.simulator.service.model.TicketIssuanceParameters;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompensatoryTicketIssuanceService {

    private final TicketProductRepository productRepository;
    private final DeviceRepository deviceRepository;
    private final StationRepository stationRepository;
    private final OperatorAccountRepository operatorRepository;
    private final PassengerAccountRepository passengerRepository;
    private final TicketQrCredentialRepository qrCredentialRepository;
    private final CompensatoryTicketIssuanceRepository issuanceRepository;
    private final TicketIssuanceEventRegistrationService eventRegistrationService;
    private final NetworkJourneyPlanningService journeyPlanningService;
    private final TicketIssuanceService ticketIssuanceService;
    private final PassengerTicketWalletDeliveryService walletDeliveryService;
    private final TicketQrImageService qrImageService;
    private final MqttDeviceCommandService commandService;
    private final Clock clock;

    public CompensatoryTicketIssuanceService(
            TicketProductRepository productRepository,
            DeviceRepository deviceRepository,
            StationRepository stationRepository,
            OperatorAccountRepository operatorRepository,
            PassengerAccountRepository passengerRepository,
            TicketQrCredentialRepository qrCredentialRepository,
            CompensatoryTicketIssuanceRepository issuanceRepository,
            TicketIssuanceEventRegistrationService eventRegistrationService,
            NetworkJourneyPlanningService journeyPlanningService,
            TicketIssuanceService ticketIssuanceService,
            PassengerTicketWalletDeliveryService walletDeliveryService,
            TicketQrImageService qrImageService,
            MqttDeviceCommandService commandService,
            Clock clock
    ) {
        this.productRepository = productRepository;
        this.deviceRepository = deviceRepository;
        this.stationRepository = stationRepository;
        this.operatorRepository = operatorRepository;
        this.passengerRepository = passengerRepository;
        this.qrCredentialRepository = qrCredentialRepository;
        this.issuanceRepository = issuanceRepository;
        this.eventRegistrationService = eventRegistrationService;
        this.journeyPlanningService = journeyPlanningService;
        this.ticketIssuanceService = ticketIssuanceService;
        this.walletDeliveryService = walletDeliveryService;
        this.qrImageService = qrImageService;
        this.commandService = commandService;
        this.clock = clock;
    }

    @Transactional
    public CompensatoryTicketIssuanceResponse issue(
            long productId,
            CompensatoryTicketIssuanceRequest request,
            Authentication authentication
    ) {
        OperatorAccount operator = authenticatedOperator(authentication);
        TicketProduct product = productRepository.findById(productId)
                .filter(TicketProduct::isActive)
                .orElseThrow(() -> notFound("Active transport title not found"));
        LocalDateTime now = LocalDateTime.now(clock);
        String issuanceCode = uniqueCode("COMP");
        CompensatoryDeliveryMethod deliveryMethod = request.deliveryMethod() == null
                ? CompensatoryDeliveryMethod.PHYSICAL_DEVICE : request.deliveryMethod();
        Device device = deliveryMethod == CompensatoryDeliveryMethod.PHYSICAL_DEVICE
                ? requiredTicketMachine(request.deviceCode()) : null;
        PassengerAccount passenger = deliveryMethod == CompensatoryDeliveryMethod.DIGITAL_WALLET
                ? requiredPassenger(request.passengerPublicId()) : null;
        rejectIncompatibleDestination(request, deliveryMethod);
        CompensatoryTicketIssuance issuance = deliveryMethod == CompensatoryDeliveryMethod.PHYSICAL_DEVICE
                ? new CompensatoryTicketIssuance(
                        issuanceCode, product, device, operator, request.reason().trim(), now)
                : new CompensatoryTicketIssuance(
                        issuanceCode, product, passenger, operator, request.reason().trim(), now);
        TicketIssuanceParameters parameters = configure(product, request, issuance);

        issuanceRepository.save(issuance);
        eventRegistrationService.registerRequested(issuance, now);
        if (deliveryMethod == CompensatoryDeliveryMethod.DIGITAL_WALLET) {
            IssuedTicket issued = walletDeliveryService.deliver(product, parameters, passenger);
            issuance.beginProcessing(issued.ticket());
            issuance.complete(now);
            issuanceRepository.save(issuance);
            eventRegistrationService.registerCompleted(issuance, now);
            return CompensatoryTicketIssuanceResponse.from(issuance);
        }

        String linkingCode = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
        IssuedTicket issued = ticketIssuanceService.issuePhysical(
                product, parameters, device,
                "RMM-COMP-PHY-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT),
                linkingCode);
        issuance.beginProcessing(issued.ticket());
        var credential = qrCredentialRepository
                .findFirstByTicketIdAndStatusOrderByIssuedAtDesc(
                        issued.ticket().getId(), TicketQrCredentialStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Compensatory ticket has no active QR"));
        Map<String, Object> ticket = new LinkedHashMap<>();
        ticket.put("ticketCode", issued.ticket().getCode());
        ticket.put("productType", product.getProductType().name());
        ticket.put("qrValue", credential.getQrValue());
        ticket.put("qrPngBase64", qrImageService.pngBase64(credential.getQrValue()));
        ticket.put("linkingCode", linkingCode);
        if (parameters.originStation() != null) {
            ticket.put("originStationCode", parameters.originStation().getCode());
            ticket.put("destinationStationCode", parameters.destinationStation().getCode());
        }
        commandService.send(device.getCode(), DeviceMqttCommandType.TICKET_ISSUE,
                Map.of("issuanceKind", "COMPENSATORY", "issuanceCode", issuance.getCode(), "ticket", ticket),
                Duration.ofMinutes(2));
        return CompensatoryTicketIssuanceResponse.from(issuance);
    }

    private Device requiredTicketMachine(String deviceCode) {
        Device device = deviceRepository.findByCodeAndActiveTrue(
                        normalizeRequiredCode(deviceCode, "deviceCode"))
                .orElseThrow(() -> notFound("Active ticket machine not found"));
        if (device.getType() != DeviceType.TICKET_MACHINE) {
            throw badRequest("Compensatory tickets can only be issued by a ticket machine");
        }
        if (device.getStatus() != DeviceStatus.ONLINE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The selected ticket machine is not online");
        }
        return device;
    }

    private PassengerAccount requiredPassenger(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw badRequest("passengerPublicId is required");
        }
        PassengerAccount passenger = passengerRepository.findByPublicId(publicId.trim())
                .orElseThrow(() -> notFound("Passenger account not found"));
        if (passenger.getStatus() != PassengerAccountStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The passenger account is not active");
        }
        return passenger;
    }

    private void rejectIncompatibleDestination(
            CompensatoryTicketIssuanceRequest request,
            CompensatoryDeliveryMethod deliveryMethod
    ) {
        if (deliveryMethod == CompensatoryDeliveryMethod.PHYSICAL_DEVICE
                && request.passengerPublicId() != null && !request.passengerPublicId().isBlank()) {
            throw badRequest("A physical delivery cannot target a passenger wallet");
        }
        if (deliveryMethod == CompensatoryDeliveryMethod.DIGITAL_WALLET
                && request.deviceCode() != null && !request.deviceCode().isBlank()) {
            throw badRequest("A digital delivery cannot target a ticket machine");
        }
    }

    private TicketIssuanceParameters configure(
            TicketProduct product,
            CompensatoryTicketIssuanceRequest request,
            CompensatoryTicketIssuance issuance
    ) {
        return switch (product.getProductType()) {
            case SINGLE_TRIP -> configureSingleTrip(request, issuance);
            case MULTI_TRIP -> {
                rejectUnexpected(request.originStationCode(), request.destinationStationCode(),
                        request.days(), request.balanceAmount());
                int trips = requireRange(request.trips(), product.getMinTrips(), product.getMaxTrips(), "trips");
                issuance.configureTripBalance(trips);
                yield TicketIssuanceParameters.multiTrip(trips);
            }
            case TIME_PASS -> {
                rejectUnexpected(request.originStationCode(), request.destinationStationCode(),
                        request.trips(), request.balanceAmount());
                int days = requireRange(request.days(), product.getMinDays(), product.getMaxDays(), "days");
                issuance.configureValidity(days);
                yield TicketIssuanceParameters.timePass(days);
            }
            case SMART_BALANCE -> {
                rejectUnexpected(request.originStationCode(), request.destinationStationCode(),
                        request.trips(), request.days());
                BigDecimal amount = requireRange(
                        request.balanceAmount(), product.getMinRechargeAmount(),
                        product.getMaxRechargeAmount(), "balanceAmount"
                );
                issuance.configureMoneyBalance(amount);
                yield TicketIssuanceParameters.smartBalance(amount);
            }
        };
    }

    private TicketIssuanceParameters configureSingleTrip(
            CompensatoryTicketIssuanceRequest request,
            CompensatoryTicketIssuance issuance
    ) {
        rejectUnexpected(request.trips(), request.days(), request.balanceAmount());
        String originCode = normalizeRequiredCode(request.originStationCode(), "originStationCode");
        String destinationCode = normalizeRequiredCode(request.destinationStationCode(), "destinationStationCode");
        if (originCode.equals(destinationCode)) {
            throw badRequest("Origin and destination stations must be different");
        }
        Station origin = requiredStation(originCode);
        Station destination = requiredStation(destinationCode);
        NetworkJourney journey;
        try {
            journey = journeyPlanningService.calculate(originCode, destinationCode);
        } catch (IllegalArgumentException | ServiceConfigurationException exception) {
            throw badRequest(exception.getMessage());
        }
        issuance.configureSingleTrip(origin, destination, journey.stationCount());
        return TicketIssuanceParameters.singleTrip(origin, destination, journey.stationCount());
    }

    private OperatorAccount authenticatedOperator(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OperatorPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return operatorRepository.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated operator no longer exists"
                ));
    }

    private Station requiredStation(String code) {
        return stationRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> notFound("Active station not found: " + code));
    }

    private int requireRange(Integer value, Integer minimum, Integer maximum, String field) {
        if (value == null || (minimum != null && value < minimum) || (maximum != null && value > maximum)) {
            throw badRequest(field + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private BigDecimal requireRange(BigDecimal value, BigDecimal minimum, BigDecimal maximum, String field) {
        if (value == null || (minimum != null && value.compareTo(minimum) < 0)
                || (maximum != null && value.compareTo(maximum) > 0)) {
            throw badRequest(field + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private void rejectUnexpected(Object... values) {
        for (Object value : values) {
            if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                throw badRequest("The request contains parameters that do not apply to this title type");
            }
        }
    }

    private String normalizeRequiredCode(String value, String field) {
        if (value == null || value.isBlank()) { throw badRequest(field + " is required"); }
        return normalizeCode(value);
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
