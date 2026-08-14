package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.Purchase;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketQrCredential;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.PurchaseRepository;
import com.transport.simulator.service.model.TicketMachineRechargeRequest;
import com.transport.simulator.service.model.TicketRechargeParameters;
import com.transport.simulator.service.model.TicketRechargeQuote;
import com.transport.simulator.ticketing.qr.VerifiedTicketQr;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketMachineRechargeServiceTests {

    private static final Long DEVICE_ID = 41L;
    private static final String REFERENCE = "9561ad31-6273-42d9-b76f-2dabb0b60955";
    private static final String QR_VALUE = "RMM:TICKET:1:signed-value";

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final PurchaseRepository purchaseRepository = mock(PurchaseRepository.class);
    private final TicketRechargeLookupService lookupService = mock(TicketRechargeLookupService.class);
    private final TicketRechargePricingService pricingService = mock(TicketRechargePricingService.class);
    private final TicketRechargeService rechargeService = mock(TicketRechargeService.class);
    private final Device device = mock(Device.class);
    private final Ticket ticket = mock(Ticket.class);
    private final TicketQrCredential credential = mock(TicketQrCredential.class);

    private TicketMachineRechargeService service;

    @BeforeEach
    void setUp() {
        service = new TicketMachineRechargeService(
                deviceRepository, purchaseRepository, lookupService, pricingService, rechargeService
        );
        when(deviceRepository.findByIdForMqttUpdate(DEVICE_ID)).thenReturn(Optional.of(device));
        when(device.getType()).thenReturn(DeviceType.TICKET_MACHINE);
        when(purchaseRepository.findByExternalReference(REFERENCE)).thenReturn(Optional.empty());
        when(credential.getTicket()).thenReturn(ticket);
        when(lookupService.requireRechargeableTicket(QR_VALUE)).thenReturn(new VerifiedTicketQr(
                null, credential, "key-1", "fingerprint"
        ));
        when(ticket.getCode()).thenReturn("RMM-TKT-001");
    }

    @Test
    void shouldAcceptTheAuthoritativeFareAndDelegateThePaidRecharge() {
        TicketRechargeParameters parameters = TicketRechargeParameters.multiTrip(5);
        TicketMachineRechargeRequest request = request(new BigDecimal("5.00"));
        TicketRechargeQuote quote = quote(new BigDecimal("5.00"));
        Purchase purchase = mock(Purchase.class);
        when(pricingService.quote(ticket, parameters)).thenReturn(quote);
        when(rechargeService.recharge(
                "RMM-TKT-001", parameters,
                com.transport.simulator.enums.PurchaseOrigin.TICKET_MACHINE,
                com.transport.simulator.enums.PaymentMethod.SIMULATED,
                REFERENCE, device, null
        )).thenReturn(purchase);
        when(purchase.getTicket()).thenReturn(ticket);

        var result = service.recharge(DEVICE_ID, request);

        assertThat(result.purchase()).isSameAs(purchase);
        assertThat(result.ticket()).isSameAs(ticket);
        assertThat(result.qrValue()).isEqualTo(QR_VALUE);
        verify(rechargeService).recharge(
                "RMM-TKT-001", parameters,
                com.transport.simulator.enums.PurchaseOrigin.TICKET_MACHINE,
                com.transport.simulator.enums.PaymentMethod.SIMULATED,
                REFERENCE, device, null
        );
    }

    @Test
    void shouldRejectPaymentThatDoesNotMatchTheCalculatedFare() {
        when(pricingService.quote(ticket, TicketRechargeParameters.multiTrip(5)))
                .thenReturn(quote(new BigDecimal("5.00")));

        assertThatThrownBy(() -> service.recharge(DEVICE_ID, request(new BigDecimal("4.99"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authoritative fare");

        verifyNoInteractions(rechargeService);
    }

    private TicketMachineRechargeRequest request(BigDecimal amount) {
        return new TicketMachineRechargeRequest(
                REFERENCE, QR_VALUE, null, null, 5, null, null, amount
        );
    }

    private TicketRechargeQuote quote(BigDecimal amount) {
        return new TicketRechargeQuote(
                "RMM-TKT-001", TicketProductType.MULTI_TRIP,
                null, null, null, 5, 9, null, null, null, amount, "EUR"
        );
    }
}
