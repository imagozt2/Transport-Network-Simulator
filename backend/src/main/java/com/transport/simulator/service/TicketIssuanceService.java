package com.transport.simulator.service;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.entity.TicketSupport;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.repository.TicketSupportRepository;
import com.transport.simulator.service.model.IssuedTicket;
import com.transport.simulator.service.model.TicketIssuanceParameters;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class TicketIssuanceService {

    private static final int PHYSICAL_LINK_VALIDITY_MINUTES = 30;

    private final TicketRepository ticketRepository;
    private final TicketSupportRepository supportRepository;
    private final TicketOperationRegistrationService operationRegistrationService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public TicketIssuanceService(
            TicketRepository ticketRepository,
            TicketSupportRepository supportRepository,
            TicketOperationRegistrationService operationRegistrationService,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.ticketRepository = ticketRepository;
        this.supportRepository = supportRepository;
        this.operationRegistrationService = operationRegistrationService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public IssuedTicket issuePhysical(
            TicketProduct product,
            TicketIssuanceParameters parameters,
            Device issuingDevice,
            String serialNumber,
            String linkingCode
    ) {
        requireActiveProduct(product);
        Objects.requireNonNull(issuingDevice, "issuingDevice is required");
        if (!issuingDevice.isActive() || issuingDevice.getType() != DeviceType.TICKET_MACHINE
                || issuingDevice.getStatus() != DeviceStatus.ONLINE) {
            throw new IllegalArgumentException("A physical ticket requires an active online ticket machine");
        }
        String normalizedSerial = requireText(serialNumber, "serialNumber").toUpperCase(Locale.ROOT);
        if (supportRepository.existsBySerialNumber(normalizedSerial)) {
            throw new IllegalArgumentException("A support already exists for the supplied serial number");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Ticket ticket = createTicket(product, parameters, null, now);
        TicketSupport support = TicketSupport.physical(
                uniqueCode("RMM-SUP"), ticket, normalizedSerial, issuingDevice,
                passwordEncoder.encode(normalizeLinkCode(linkingCode)),
                now.plusMinutes(PHYSICAL_LINK_VALIDITY_MINUTES), now
        );
        return persist(ticket, support);
    }

    @Transactional
    public IssuedTicket issueDigital(
            TicketProduct product,
            TicketIssuanceParameters parameters,
            PassengerAccount passenger
    ) {
        requireActiveProduct(product);
        Objects.requireNonNull(passenger, "passenger is required");
        if (passenger.getStatus() != PassengerAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("A digital ticket requires an active passenger account");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Ticket ticket = createTicket(product, parameters, passenger, now);
        TicketSupport support = TicketSupport.digital(
                uniqueCode("RMM-SUP"), ticket, passenger, now
        );
        return persist(ticket, support);
    }

    private Ticket createTicket(
            TicketProduct product,
            TicketIssuanceParameters parameters,
            PassengerAccount passenger,
            LocalDateTime now
    ) {
        Objects.requireNonNull(parameters, "parameters are required");
        Ticket ticket = new Ticket(uniqueCode("RMM-TKT"), UUID.randomUUID().toString(), product, now);
        if (passenger != null) {
            ticket.assignPassenger(passenger);
        }

        switch (product.getProductType()) {
            case SINGLE_TRIP -> configureSingleTrip(ticket, parameters);
            case MULTI_TRIP -> ticket.configureTripBalance(requireRange(
                    parameters.trips(), product.getMinTrips(), product.getMaxTrips(), "trips"
            ));
            case TIME_PASS -> ticket.configureValidity(requireRange(
                    parameters.days(), product.getMinDays(), product.getMaxDays(), "days"
            ), now);
            case SMART_BALANCE -> ticket.configureMoneyBalance(requireRange(
                    parameters.balanceAmount(), product.getMinRechargeAmount(),
                    product.getMaxRechargeAmount(), "balanceAmount"
            ));
        }
        rejectUnexpectedParameters(product, parameters);
        return ticket;
    }

    private void configureSingleTrip(Ticket ticket, TicketIssuanceParameters parameters) {
        if (parameters.originStation() == null || parameters.destinationStation() == null
                || parameters.originStation().equals(parameters.destinationStation())
                || parameters.stationCount() == null || parameters.stationCount() <= 0) {
            throw new IllegalArgumentException(
                    "A single ticket requires different origin and destination stations and a positive distance"
            );
        }
        ticket.configureSingleTrip(
                parameters.originStation(), parameters.destinationStation(), parameters.stationCount()
        );
    }

    private void rejectUnexpectedParameters(
            TicketProduct product,
            TicketIssuanceParameters parameters
    ) {
        boolean valid = switch (product.getProductType()) {
            case SINGLE_TRIP -> parameters.trips() == null && parameters.days() == null
                    && parameters.balanceAmount() == null;
            case MULTI_TRIP -> parameters.originStation() == null && parameters.destinationStation() == null
                    && parameters.stationCount() == null && parameters.days() == null
                    && parameters.balanceAmount() == null;
            case TIME_PASS -> parameters.originStation() == null && parameters.destinationStation() == null
                    && parameters.stationCount() == null && parameters.trips() == null
                    && parameters.balanceAmount() == null;
            case SMART_BALANCE -> parameters.originStation() == null && parameters.destinationStation() == null
                    && parameters.stationCount() == null && parameters.trips() == null
                    && parameters.days() == null;
        };
        if (!valid) {
            throw new IllegalArgumentException("The issuance contains parameters for another product type");
        }
    }

    private IssuedTicket persist(Ticket ticket, TicketSupport support) {
        Ticket persistedTicket = ticketRepository.save(ticket);
        TicketSupport persistedSupport = supportRepository.save(support);
        operationRegistrationService.recordIssuance(persistedTicket, persistedSupport);
        return new IssuedTicket(persistedTicket, persistedSupport);
    }

    private void requireActiveProduct(TicketProduct product) {
        Objects.requireNonNull(product, "product is required");
        if (!product.isActive()) {
            throw new IllegalArgumentException("Tickets cannot be issued for an inactive product");
        }
    }

    private int requireRange(Integer value, Integer minimum, Integer maximum, String field) {
        if (value == null || minimum == null || maximum == null || value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private BigDecimal requireRange(
            BigDecimal value,
            BigDecimal minimum,
            BigDecimal maximum,
            String field
    ) {
        if (value == null || minimum == null || maximum == null
                || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String normalizeLinkCode(String value) {
        String code = requireText(value, "linkingCode")
                .replace("-", "")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
        if (code.length() < 4 || code.length() > 32) {
            throw new IllegalArgumentException("linkingCode must contain between 4 and 32 characters");
        }
        return code;
    }
}
