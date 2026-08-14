package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.Purchase;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.PaymentMethod;
import com.transport.simulator.enums.PurchaseOrigin;
import com.transport.simulator.enums.PurchaseType;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.repository.PurchaseRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.service.model.TicketRechargeParameters;
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
                mock(TicketOperationRegistrationService.class),
                pricingService,
                Clock.systemUTC()
        );
        when(ticketRepository.findByCodeForUpdate("RMM-TKT-001")).thenReturn(Optional.of(ticket));
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
        assertThatThrownBy(() -> service.recharge(
                "RMM-TKT-001", TicketRechargeParameters.multiTrip(6),
                PurchaseOrigin.TICKET_MACHINE, PaymentMethod.SIMULATED,
                REFERENCE, device, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already used");

        verifyNoInteractions(multiTripService, pricingService);
    }
}
