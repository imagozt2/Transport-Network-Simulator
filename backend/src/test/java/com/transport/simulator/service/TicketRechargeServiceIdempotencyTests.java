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
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.PaymentMethod;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.PurchaseOrigin;
import com.transport.simulator.enums.PurchaseType;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.repository.PurchaseRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.service.model.TicketRechargeParameters;
import com.transport.simulator.service.model.TicketRechargeQuote;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketRechargeServiceIdempotencyTests {

    private static final String REFERENCE = "9561ad31-6273-42d9-b76f-2dabb0b60955";

    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final PurchaseRepository purchaseRepository = mock(PurchaseRepository.class);
    private final MultiTripTicketService multiTripService = mock(MultiTripTicketService.class);
    private final TicketRechargePricingService pricingService = mock(TicketRechargePricingService.class);
    private final TicketOperationRegistrationService operationRegistrationService =
            mock(TicketOperationRegistrationService.class);
    private final Ticket ticket = mock(Ticket.class);
    private final Purchase previous = mock(Purchase.class);
    private final Device device = mock(Device.class);
    private final TicketProduct product = mock(TicketProduct.class);

    private TicketRechargeService service;

    @BeforeEach
    void setUp() {
        service = new TicketRechargeService(
                ticketRepository,
                purchaseRepository,
                mock(SingleTripTicketService.class),
                multiTripService,
                mock(TimePassTicketService.class),
                mock(SmartBalanceTicketService.class),
                operationRegistrationService,
                pricingService,
                Clock.systemUTC()
        );
        when(ticketRepository.findByCodeForUpdate("RMM-TKT-001")).thenReturn(Optional.of(ticket));
    }

    private void preparePreviousRecharge() {
        when(purchaseRepository.findByExternalReference(REFERENCE)).thenReturn(Optional.of(previous));
        when(previous.getType()).thenReturn(PurchaseType.RECHARGE);
        when(previous.getTicket()).thenReturn(ticket);
        when(ticket.getCode()).thenReturn("RMM-TKT-001");
        when(previous.getOrigin()).thenReturn(PurchaseOrigin.TICKET_MACHINE);
        when(previous.getPaymentMethod()).thenReturn(PaymentMethod.SIMULATED);
        when(previous.getDevice()).thenReturn(device);
        when(device.getId()).thenReturn(8L);
        when(previous.getProduct()).thenReturn(product);
        when(product.getProductType()).thenReturn(TicketProductType.MULTI_TRIP);
        when(previous.getSelectedTrips()).thenReturn(5);
    }

    @Test
    void shouldReturnTheOriginalPurchaseForAnIdenticalRetry() {
        preparePreviousRecharge();

        Purchase result = service.recharge(
                "RMM-TKT-001", TicketRechargeParameters.multiTrip(5),
                PurchaseOrigin.TICKET_MACHINE, PaymentMethod.SIMULATED,
                REFERENCE, device, null
        );

        assertThat(result).isSameAs(previous);
        verifyNoInteractions(multiTripService, pricingService);
    }

    @Test
    void shouldRejectReuseOfTheReferenceWithDifferentParameters() {
        preparePreviousRecharge();

        assertThatThrownBy(() -> service.recharge(
                "RMM-TKT-001", TicketRechargeParameters.multiTrip(6),
                PurchaseOrigin.TICKET_MACHINE, PaymentMethod.SIMULATED,
                REFERENCE, device, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already used");

        verifyNoInteractions(multiTripService, pricingService);
    }

    @Test
    void shouldPersistANewPaidRechargeAndItsTicketOperation() {
        Ticket updated = mock(Ticket.class);
        TicketRechargeParameters parameters = TicketRechargeParameters.multiTrip(5);
        TicketRechargeQuote quote = new TicketRechargeQuote(
                "RMM-TKT-001", TicketProductType.MULTI_TRIP,
                null, null, null, 5, 9, null, null, null,
                new BigDecimal("5.00"), "EUR"
        );
        when(purchaseRepository.findByExternalReference(REFERENCE)).thenReturn(Optional.empty());
        when(ticket.getCode()).thenReturn("RMM-TKT-001");
        when(ticket.getProduct()).thenReturn(product);
        when(product.isRechargeable()).thenReturn(true);
        when(ticket.getProductType()).thenReturn(TicketProductType.MULTI_TRIP);
        when(ticket.getCurrency()).thenReturn("EUR");
        when(device.isActive()).thenReturn(true);
        when(device.getType()).thenReturn(DeviceType.TICKET_MACHINE);
        when(device.getStatus()).thenReturn(DeviceStatus.ONLINE);
        when(pricingService.quote(ticket, parameters)).thenReturn(quote);
        when(multiTripService.recharge("RMM-TKT-001", 5)).thenReturn(updated);
        when(updated.getProduct()).thenReturn(product);
        when(updated.getProductType()).thenReturn(TicketProductType.MULTI_TRIP);
        when(updated.getCurrency()).thenReturn("EUR");
        when(purchaseRepository.save(org.mockito.ArgumentMatchers.any(Purchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Purchase result = service.recharge(
                "rmm-tkt-001", parameters, PurchaseOrigin.TICKET_MACHINE,
                PaymentMethod.SIMULATED, REFERENCE, device, null
        );

        assertThat(result.getType()).isEqualTo(PurchaseType.RECHARGE);
        assertThat(result.getExternalReference()).isEqualTo(REFERENCE);
        assertThat(result.getSelectedTrips()).isEqualTo(5);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("5.00");
        assertThat(result.getTicket()).isSameAs(updated);
        verify(purchaseRepository).save(result);
        verify(operationRegistrationService).recordRecharge(
                org.mockito.ArgumentMatchers.eq(result),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(device),
                org.mockito.ArgumentMatchers.isNull()
        );
    }
}
