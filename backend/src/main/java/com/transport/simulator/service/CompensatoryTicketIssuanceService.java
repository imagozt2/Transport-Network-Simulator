package com.transport.simulator.service;

import com.transport.simulator.dto.request.transporttitle.CompensatoryTicketIssuanceRequest;
import com.transport.simulator.dto.response.transporttitle.CompensatoryTicketIssuanceResponse;
import com.transport.simulator.entity.CompensatoryTicketIssuance;
import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.repository.CompensatoryTicketIssuanceRepository;
import com.transport.simulator.repository.DeviceEventLogRepository;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.OperatorAccountRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketProductRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.security.OperatorPrincipal;
import com.transport.simulator.service.model.NetworkJourney;
import java.math.BigDecimal;
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
public class CompensatoryTicketIssuanceService {

    private final TicketProductRepository productRepository;
    private final DeviceRepository deviceRepository;
    private final StationRepository stationRepository;
    private final OperatorAccountRepository operatorRepository;
    private final TicketRepository ticketRepository;
    private final CompensatoryTicketIssuanceRepository issuanceRepository;
    private final DeviceEventLogRepository logRepository;
    private final NetworkJourneyPlanningService journeyPlanningService;
    private final Clock clock;

    public CompensatoryTicketIssuanceService(
            TicketProductRepository productRepository,
            DeviceRepository deviceRepository,
            StationRepository stationRepository,
            OperatorAccountRepository operatorRepository,
            TicketRepository ticketRepository,
            CompensatoryTicketIssuanceRepository issuanceRepository,
            DeviceEventLogRepository logRepository,
            NetworkJourneyPlanningService journeyPlanningService,
            Clock clock
    ) {
        this.productRepository = productRepository;
        this.deviceRepository = deviceRepository;
        this.stationRepository = stationRepository;
        this.operatorRepository = operatorRepository;
        this.ticketRepository = ticketRepository;
        this.issuanceRepository = issuanceRepository;
        this.logRepository = logRepository;
        this.journeyPlanningService = journeyPlanningService;
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
        Device device = deviceRepository.findByCodeAndActiveTrue(normalizeCode(request.deviceCode()))
                .orElseThrow(() -> notFound("Active ticket machine not found"));
        if (device.getType() != DeviceType.TICKET_MACHINE) {
            throw badRequest("Compensatory tickets can only be issued by a ticket machine");
        }
        if (device.getStatus() != DeviceStatus.ONLINE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The selected ticket machine is not online");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String issuanceCode = uniqueCode("COMP");
        CompensatoryTicketIssuance issuance = new CompensatoryTicketIssuance(
                issuanceCode, product, device, operator, request.reason().trim(), now
        );
        Ticket ticket = new Ticket(uniqueCode("RMM"), UUID.randomUUID().toString(), product, now);
        configure(product, request, issuance, ticket, now);

        ticketRepository.save(ticket);
        issuance.complete(ticket, now);
        issuanceRepository.save(issuance);

        DeviceEventLog log = new DeviceEventLog(
                LogOrigin.ADMINISTRATION,
                DeviceEventType.COMPENSATORY_TICKET_ISSUED,
                LogSeverity.INFO,
                "Emisión compensatoria de " + product.getProductType()
                        + " completada por " + operator.getUsername(),
                device,
                now,
                issuanceCode,
                payload(issuanceCode, ticket.getCode(), product)
        );
        log.linkCompensatoryIssuance(issuance, ticket);
        logRepository.save(log);
        return CompensatoryTicketIssuanceResponse.from(issuance);
    }

    private void configure(
            TicketProduct product,
            CompensatoryTicketIssuanceRequest request,
            CompensatoryTicketIssuance issuance,
            Ticket ticket,
            LocalDateTime now
    ) {
        switch (product.getProductType()) {
            case SINGLE_TRIP -> configureSingleTrip(request, issuance, ticket);
            case MULTI_TRIP -> {
                rejectUnexpected(request.originStationCode(), request.destinationStationCode(),
                        request.days(), request.balanceAmount());
                int trips = requireRange(request.trips(), product.getMinTrips(), product.getMaxTrips(), "trips");
                issuance.configureTripBalance(trips);
                ticket.configureTripBalance(trips);
            }
            case TIME_PASS -> {
                rejectUnexpected(request.originStationCode(), request.destinationStationCode(),
                        request.trips(), request.balanceAmount());
                int days = requireRange(request.days(), product.getMinDays(), product.getMaxDays(), "days");
                issuance.configureValidity(days);
                ticket.configureValidity(days, now);
            }
            case SMART_BALANCE -> {
                rejectUnexpected(request.originStationCode(), request.destinationStationCode(),
                        request.trips(), request.days());
                BigDecimal amount = requireRange(
                        request.balanceAmount(), product.getMinRechargeAmount(),
                        product.getMaxRechargeAmount(), "balanceAmount"
                );
                issuance.configureMoneyBalance(amount);
                ticket.configureMoneyBalance(amount);
            }
        }
    }

    private void configureSingleTrip(
            CompensatoryTicketIssuanceRequest request,
            CompensatoryTicketIssuance issuance,
            Ticket ticket
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
        ticket.configureSingleTrip(origin, destination, journey.stationCount());
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

    private String payload(String issuanceCode, String ticketCode, TicketProduct product) {
        return "{\"issuanceCode\":\"" + issuanceCode + "\",\"ticketCode\":\""
                + ticketCode + "\",\"ticketType\":\"" + product.getProductType() + "\"}";
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
