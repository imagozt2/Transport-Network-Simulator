package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.request.passengerticket.PassengerTicketConfigurationRequest;
import com.transport.simulator.dto.request.passengerticket.PassengerTicketPurchaseRequest;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.Purchase;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.PaymentMethod;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.repository.PurchaseRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketProductRepository;
import com.transport.simulator.service.model.IssuedTicket;
import com.transport.simulator.service.model.NetworkJourney;
import com.transport.simulator.service.model.TicketIssuanceParameters;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class PassengerTicketPurchaseServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-10T10:00:00Z"), ZoneId.of("Europe/Madrid")
    );

    @Mock private PassengerResourceAccessService accessService;
    @Mock private TicketProductRepository productRepository;
    @Mock private StationRepository stationRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private NetworkJourneyPlanningService journeyPlanningService;
    @Mock private TicketIssuanceService issuanceService;
    @Mock private Authentication authentication;
    @Mock private PassengerAccount passenger;

    private PassengerTicketPurchaseService service;

    @BeforeEach
    void setUp() {
        service = new PassengerTicketPurchaseService(
                accessService, productRepository, stationRepository, purchaseRepository,
                journeyPlanningService, issuanceService, CLOCK
        );
    }

    @Test
    void shouldCalculateAndPersistASingleTripPurchase() {
        TicketProduct product = product(TicketProductType.SINGLE_TRIP);
        Station origin = station(1L, "ST001");
        Station destination = station(2L, "ST007");
        preparePurchase(product);
        when(stationRepository.findByCodeAndActiveTrue("ST001")).thenReturn(Optional.of(origin));
        when(stationRepository.findByCodeAndActiveTrue("ST007")).thenReturn(Optional.of(destination));
        when(journeyPlanningService.calculate("ST001", "ST007")).thenReturn(new NetworkJourney(
                null, null, 7, 0, 600, List.of(), List.of()
        ));

        Purchase purchase = service.purchase("single-trip-request-0001", request(
                "SINGLE_TRIP", new PassengerTicketConfigurationRequest(
                        "ST001", "ST007", null, null, null
                )
        ), authentication);

        assertThat(purchase.getTotalAmount()).isEqualByComparingTo("0.85");
        ArgumentCaptor<TicketIssuanceParameters> parameters =
                ArgumentCaptor.forClass(TicketIssuanceParameters.class);
        verify(issuanceService).issueDigital(any(), parameters.capture(), any());
        assertThat(parameters.getValue().stationCount()).isEqualTo(7);
        assertThat(parameters.getValue().originStation()).isSameAs(origin);
        assertThat(parameters.getValue().destinationStation()).isSameAs(destination);
    }

    @Test
    void shouldCalculateMultiTripTimePassAndSmartBalancePrices() {
        assertPurchasePrice(
                TicketProductType.MULTI_TRIP,
                new PassengerTicketConfigurationRequest(null, null, 12, null, null),
                "12.00"
        );
        assertPurchasePrice(
                TicketProductType.TIME_PASS,
                new PassengerTicketConfigurationRequest(null, null, null, 5, null),
                "10.00"
        );
        assertPurchasePrice(
                TicketProductType.SMART_BALANCE,
                new PassengerTicketConfigurationRequest(null, null, null, null, new BigDecimal("17.35")),
                "17.35"
        );
    }

    @Test
    void shouldReturnThePreviousPurchaseWithoutIssuingAnotherTicket() {
        when(accessService.currentAccount(authentication)).thenReturn(passenger);
        when(passenger.getId()).thenReturn(9L);
        Purchase existing = org.mockito.Mockito.mock(Purchase.class);
        when(existing.getPassengerAccount()).thenReturn(passenger);
        when(purchaseRepository.findByExternalReference("repeated-request-0001"))
                .thenReturn(Optional.of(existing));

        Purchase result = service.purchase(
                "repeated-request-0001",
                request("MULTI_TRIP", new PassengerTicketConfigurationRequest(null, null, 10, null, null)),
                authentication
        );

        assertThat(result).isSameAs(existing);
        verify(issuanceService, never()).issueDigital(any(), any(), any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void shouldNotPersistAPurchaseWhenTicketIssuanceFails() {
        TicketProduct product = product(TicketProductType.MULTI_TRIP);
        prepareBase(product);
        when(issuanceService.issueDigital(any(), any(), any()))
                .thenThrow(new IllegalStateException("Ticket QR could not be signed"));

        assertThatThrownBy(() -> service.purchase(
                "failed-issuance-request-0001",
                request("MULTI_TRIP", new PassengerTicketConfigurationRequest(null, null, 10, null, null)),
                authentication
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be signed");

        verify(purchaseRepository, never()).save(any());
    }

    private void assertPurchasePrice(
            TicketProductType type,
            PassengerTicketConfigurationRequest configuration,
            String expected
    ) {
        org.mockito.Mockito.reset(
                accessService, productRepository, purchaseRepository, issuanceService, passenger
        );
        TicketProduct product = product(type);
        preparePurchase(product);

        Purchase purchase = service.purchase(
                "price-request-" + type.name().toLowerCase(),
                request(type.name(), configuration),
                authentication
        );

        assertThat(purchase.getTotalAmount()).isEqualByComparingTo(expected);
    }

    private void preparePurchase(TicketProduct product) {
        prepareBase(product);
        Ticket ticket = org.mockito.Mockito.mock(Ticket.class);
        when(ticket.getProduct()).thenReturn(product);
        when(ticket.getCurrency()).thenReturn("EUR");
        when(issuanceService.issueDigital(any(), any(), any())).thenReturn(new IssuedTicket(ticket, null));
        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void prepareBase(TicketProduct product) {
        when(accessService.currentAccount(authentication)).thenReturn(passenger);
        when(purchaseRepository.findByExternalReference(any())).thenReturn(Optional.empty());
        when(productRepository.findByCodeIgnoreCase(product.getProductType().name()))
                .thenReturn(Optional.of(product));
    }

    private TicketProduct product(TicketProductType type) {
        TicketProduct product = org.mockito.Mockito.mock(TicketProduct.class);
        when(product.getProductType()).thenReturn(type);
        when(product.isActive()).thenReturn(true);
        switch (type) {
            case SINGLE_TRIP -> {
                when(product.getBasePrice()).thenReturn(new BigDecimal("0.50"));
                when(product.getPricePerStation()).thenReturn(new BigDecimal("0.05"));
            }
            case MULTI_TRIP -> when(product.getPricePerTrip()).thenReturn(new BigDecimal("1.00"));
            case TIME_PASS -> when(product.getPricePerDay()).thenReturn(new BigDecimal("2.00"));
            case SMART_BALANCE -> {
                // El importe inicial coincide con la recarga elegida por el pasajero.
            }
        }
        return product;
    }

    private Station station(Long id, String code) {
        Station station = org.mockito.Mockito.mock(Station.class);
        when(station.getId()).thenReturn(id);
        when(station.getCode()).thenReturn(code);
        return station;
    }

    private PassengerTicketPurchaseRequest request(
            String productCode,
            PassengerTicketConfigurationRequest configuration
    ) {
        return new PassengerTicketPurchaseRequest(productCode, configuration, PaymentMethod.SIMULATED);
    }
}
