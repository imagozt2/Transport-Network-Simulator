package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.response.ticketrecharge.TicketRechargeLookupResponse;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.entity.TicketQrCredential;
import com.transport.simulator.entity.TicketSupport;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.enums.TicketStatus;
import com.transport.simulator.enums.TicketSupportStatus;
import com.transport.simulator.enums.TicketSupportType;
import com.transport.simulator.ticketing.qr.TicketQrVerificationException;
import com.transport.simulator.ticketing.qr.TicketQrVerificationFailure;
import com.transport.simulator.ticketing.qr.TicketQrVerifier;
import com.transport.simulator.ticketing.qr.VerifiedTicketQr;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TicketRechargeLookupServiceTests {

    private static final String QR_VALUE = "RMM:TICKET:1:signed-value";

    @Mock private TicketQrVerifier qrVerifier;
    @Mock private TicketQrCredential credential;
    @Mock private TicketSupport support;
    @Mock private Ticket ticket;
    @Mock private TicketProduct product;

    private TicketRechargeLookupService service;

    @BeforeEach
    void setUp() {
        service = new TicketRechargeLookupService(qrVerifier);
    }

    @Test
    void shouldReturnTheRechargeContractForAValidQrWithoutChangingTheTicket() {
        prepareRechargeableTicket(TicketProductType.MULTI_TRIP, TicketStatus.ACTIVE);
        when(ticket.getCode()).thenReturn("RMM-TKT-001");
        when(ticket.getRemainingTrips()).thenReturn(4);
        when(ticket.getBalanceAmount()).thenReturn(BigDecimal.ZERO);
        when(ticket.getCurrency()).thenReturn("EUR");
        when(product.getCode()).thenReturn("MULTI_TRIP");
        when(product.getName()).thenReturn("Billete multiviaje");
        when(product.getMinTrips()).thenReturn(2);
        when(product.getMaxTrips()).thenReturn(30);
        when(product.getPricePerTrip()).thenReturn(new BigDecimal("1.00"));

        TicketRechargeLookupResponse result = service.findRechargeableTicket(QR_VALUE);

        assertThat(result.ticketCode()).isEqualTo("RMM-TKT-001");
        assertThat(result.productType()).isEqualTo("MULTI_TRIP");
        assertThat(result.ticketStatus()).isEqualTo("ACTIVE");
        assertThat(result.supportType()).isEqualTo(TicketSupportType.PHYSICAL);
        assertThat(result.remainingTrips()).isEqualTo(4);
        assertThat(result.minTrips()).isEqualTo(2);
        assertThat(result.maxTrips()).isEqualTo(30);
        assertThat(result.pricePerTrip()).isEqualByComparingTo("1.00");
        verify(qrVerifier).verify(QR_VALUE);
    }

    @Test
    void shouldRejectAValidQrWhoseTicketCannotBeRecharged() {
        prepareRechargeableTicket(TicketProductType.SINGLE_TRIP, TicketStatus.ACTIVE);

        assertThatThrownBy(() -> service.findRechargeableTicket(QR_VALUE))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getReason()).isEqualTo("TICKET_NOT_RECHARGEABLE");
                });
    }

    @Test
    void shouldHideTheReasonWhyQrVerificationFailed() {
        when(qrVerifier.verify(QR_VALUE)).thenThrow(new TicketQrVerificationException(
                TicketQrVerificationFailure.INVALID_SIGNATURE
        ));

        assertThatThrownBy(() -> service.findRechargeableTicket(QR_VALUE))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getReason()).isEqualTo("INVALID_TICKET_QR");
                });
    }

    private void prepareRechargeableTicket(TicketProductType type, TicketStatus status) {
        when(qrVerifier.verify(QR_VALUE)).thenReturn(new VerifiedTicketQr(
                null, credential, "key-1", "fingerprint"
        ));
        when(credential.getTicket()).thenReturn(ticket);
        when(credential.getSupport()).thenReturn(support);
        when(support.getStatus()).thenReturn(TicketSupportStatus.ACTIVE);
        lenient().when(support.getType()).thenReturn(TicketSupportType.PHYSICAL);
        when(ticket.isActive()).thenReturn(true);
        when(ticket.getProduct()).thenReturn(product);
        when(ticket.getProductType()).thenReturn(type);
        when(ticket.getStatus()).thenReturn(status);
        when(product.isActive()).thenReturn(true);
        when(product.isRechargeable()).thenReturn(true);
        lenient().when(product.getProductType()).thenReturn(type);
    }
}
