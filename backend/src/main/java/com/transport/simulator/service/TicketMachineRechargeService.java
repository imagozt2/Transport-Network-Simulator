package com.transport.simulator.service;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.Purchase;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.PaymentMethod;
import com.transport.simulator.enums.PurchaseOrigin;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.PurchaseRepository;
import com.transport.simulator.service.model.TicketMachineRechargeRequest;
import com.transport.simulator.service.model.TicketMachineRechargeResult;
import com.transport.simulator.service.model.TicketRechargeQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketMachineRechargeService {

    private final DeviceRepository deviceRepository;
    private final PurchaseRepository purchaseRepository;
    private final TicketRechargeLookupService lookupService;
    private final TicketRechargePricingService pricingService;
    private final TicketRechargeService rechargeService;

    public TicketMachineRechargeService(
            DeviceRepository deviceRepository,
            PurchaseRepository purchaseRepository,
            TicketRechargeLookupService lookupService,
            TicketRechargePricingService pricingService,
            TicketRechargeService rechargeService
    ) {
        this.deviceRepository = deviceRepository;
        this.purchaseRepository = purchaseRepository;
        this.lookupService = lookupService;
        this.pricingService = pricingService;
        this.rechargeService = rechargeService;
    }

    @Transactional
    public TicketMachineRechargeResult recharge(
            Long authenticatedDeviceId,
            TicketMachineRechargeRequest request
    ) {
        String reference = requireReference(request.rechargeReference());
        Device device = deviceRepository.findByIdForMqttUpdate(authenticatedDeviceId)
                .filter(candidate -> candidate.getType() == DeviceType.TICKET_MACHINE)
                .orElseThrow(() -> new IllegalArgumentException("Active ticket machine not found"));

        Purchase previous = purchaseRepository.findByExternalReference(reference).orElse(null);
        Ticket ticket;
        if (previous != null) {
            ticket = lookupService.verifyTicket(request.qrValue()).credential().getTicket();
            if (!previous.getTicket().getCode().equals(ticket.getCode())
                    || previous.getTotalAmount().compareTo(money(request.paidAmount())) != 0) {
                throw new IllegalArgumentException("The recharge reference is already in use");
            }
        } else {
            ticket = lookupService.requireRechargeableTicket(request.qrValue())
                    .credential().getTicket();
            TicketRechargeQuote quote = pricingService.quote(ticket, request.parameters());
            if (quote.totalAmount().setScale(2, RoundingMode.HALF_UP)
                    .compareTo(money(request.paidAmount())) != 0) {
                throw new IllegalArgumentException("Paid amount does not match the authoritative fare");
            }
        }

        Purchase purchase = rechargeService.recharge(
                ticket.getCode(), request.parameters(), PurchaseOrigin.TICKET_MACHINE,
                PaymentMethod.SIMULATED, reference, device, null
        );
        return new TicketMachineRechargeResult(purchase, purchase.getTicket(), request.qrValue());
    }

    private String requireReference(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("rechargeReference is required");
        }
        String reference = UUID.fromString(value.trim()).toString();
        return reference;
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("A positive paidAmount is required");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
