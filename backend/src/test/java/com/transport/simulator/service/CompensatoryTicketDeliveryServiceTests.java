package com.transport.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transport.simulator.dto.request.transporttitle.CompensatoryTicketIssuanceRequest;
import com.transport.simulator.entity.CompensatoryTicketIssuance;
import com.transport.simulator.entity.Device;
import com.transport.simulator.entity.OperatorAccount;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.Station;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.entity.TicketQrCredential;
import com.transport.simulator.enums.CompensatoryDeliveryMethod;
import com.transport.simulator.enums.CompensatoryIssuanceStatus;
import com.transport.simulator.enums.DeviceMqttCommandType;
import com.transport.simulator.enums.DeviceMqttPresence;
import com.transport.simulator.enums.DeviceStatus;
import com.transport.simulator.enums.DeviceType;
import com.transport.simulator.enums.OperatorRole;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.enums.TicketQrCredentialStatus;
import com.transport.simulator.mqtt.MqttDeviceCommandService;
import com.transport.simulator.repository.CompensatoryTicketIssuanceRepository;
import com.transport.simulator.repository.DeviceRepository;
import com.transport.simulator.repository.OperatorAccountRepository;
import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.StationRepository;
import com.transport.simulator.repository.TicketProductRepository;
import com.transport.simulator.repository.TicketQrCredentialRepository;
import com.transport.simulator.security.OperatorPrincipal;
import com.transport.simulator.service.model.IssuedTicket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class CompensatoryTicketDeliveryServiceTests {

    private final TicketProductRepository productRepository = mock(TicketProductRepository.class);
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final StationRepository stationRepository = mock(StationRepository.class);
    private final OperatorAccountRepository operatorRepository = mock(OperatorAccountRepository.class);
    private final PassengerAccountRepository passengerRepository = mock(PassengerAccountRepository.class);
    private final TicketQrCredentialRepository credentialRepository = mock(TicketQrCredentialRepository.class);
    private final CompensatoryTicketIssuanceRepository issuanceRepository =
            mock(CompensatoryTicketIssuanceRepository.class);
    private final TicketIssuanceEventRegistrationService eventService =
            mock(TicketIssuanceEventRegistrationService.class);
    private final NetworkJourneyPlanningService journeyService = mock(NetworkJourneyPlanningService.class);
    private final TicketIssuanceService ticketIssuanceService = mock(TicketIssuanceService.class);
    private final PassengerTicketWalletDeliveryService walletDeliveryService =
            mock(PassengerTicketWalletDeliveryService.class);
    private final TicketQrImageService qrImageService = mock(TicketQrImageService.class);
    private final MqttDeviceCommandService commandService = mock(MqttDeviceCommandService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneOffset.UTC);
    private final TicketProduct product = mock(TicketProduct.class);
    private final OperatorAccount operator = mock(OperatorAccount.class);
    private CompensatoryTicketIssuanceService service;

    @BeforeEach
    void setUp() {
        when(product.isActive()).thenReturn(true);
        when(product.getCode()).thenReturn("MULTI_TRIP");
        when(product.getName()).thenReturn("Billete multiviaje");
        when(product.getProductType()).thenReturn(TicketProductType.MULTI_TRIP);
        when(product.getMinTrips()).thenReturn(2);
        when(product.getMaxTrips()).thenReturn(30);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(operator.getUsername()).thenReturn("admin");
        when(operatorRepository.findById(7L)).thenReturn(Optional.of(operator));
        when(issuanceRepository.save(any(CompensatoryTicketIssuance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new CompensatoryTicketIssuanceService(
                productRepository, deviceRepository, stationRepository, operatorRepository,
                passengerRepository, credentialRepository, issuanceRepository, eventService,
                journeyService, ticketIssuanceService, walletDeliveryService, qrImageService,
                commandService, clock
        );
    }

    @Test
    void shouldCompleteADigitalIssuanceWithAStableQrResult() {
        PassengerAccount passenger = mock(PassengerAccount.class);
        Ticket ticket = mock(Ticket.class);
        TicketQrCredential credential = mock(TicketQrCredential.class);
        when(passenger.getStatus()).thenReturn(PassengerAccountStatus.ACTIVE);
        when(passenger.getPublicId()).thenReturn("passenger-1");
        when(passenger.getEmail()).thenReturn("ana@example.com");
        when(passengerRepository.findByPublicId("passenger-1")).thenReturn(Optional.of(passenger));
        when(ticket.getId()).thenReturn(81L);
        when(ticket.getCode()).thenReturn("RMM-DIGITAL-081");
        when(ticket.getQrToken()).thenReturn("qr-token-81");
        when(walletDeliveryService.deliver(any(), any(), any()))
                .thenReturn(new IssuedTicket(ticket, null));
        when(credential.getQrValue()).thenReturn("signed-digital-qr");
        when(credentialRepository.findFirstByTicketIdAndStatusOrderByIssuedAtDesc(
                81L, TicketQrCredentialStatus.ACTIVE)).thenReturn(Optional.of(credential));
        when(qrImageService.pngBase64("signed-digital-qr")).thenReturn("digital-qr-png");

        var response = service.issue(1L, request(
                CompensatoryDeliveryMethod.DIGITAL_WALLET, null, "passenger-1"), authentication());

        assertThat(response.status()).isEqualTo(CompensatoryIssuanceStatus.COMPLETED);
        assertThat(response.deliveryMethod()).isEqualTo(CompensatoryDeliveryMethod.DIGITAL_WALLET);
        assertThat(response.ticketCode()).isEqualTo("RMM-DIGITAL-081");
        assertThat(response.productName()).isEqualTo("Billete multiviaje");
        assertThat(response.qrPngBase64()).isEqualTo("digital-qr-png");
        assertThat(response.simulated()).isFalse();
        verify(eventService).registerRequested(any(), any());
        verify(eventService).registerCompleted(any(), any());
        verify(commandService, never()).send(any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendAPhysicalIssuanceWithItsQrToAConnectedMqttMachine() {
        Device device = mock(Device.class);
        Station station = mock(Station.class);
        Ticket ticket = mock(Ticket.class);
        TicketQrCredential credential = mock(TicketQrCredential.class);
        when(device.getCode()).thenReturn("RMM-TM-ST001-01");
        when(device.getName()).thenReturn("Máquina de venta Aeropuerto 1");
        when(device.getType()).thenReturn(DeviceType.TICKET_MACHINE);
        when(device.getStatus()).thenReturn(DeviceStatus.ONLINE);
        when(device.isMqttManaged()).thenReturn(true);
        when(device.getMqttPresence()).thenReturn(DeviceMqttPresence.ONLINE);
        when(device.getStation()).thenReturn(station);
        when(station.getCode()).thenReturn("ST001");
        when(station.getName()).thenReturn("Aeropuerto");
        when(deviceRepository.findByCodeAndActiveTrue("RMM-TM-ST001-01"))
                .thenReturn(Optional.of(device));
        when(ticket.getId()).thenReturn(82L);
        when(ticket.getCode()).thenReturn("RMM-PHYSICAL-082");
        when(ticketIssuanceService.issuePhysical(any(), any(), any(), any(), any()))
                .thenReturn(new IssuedTicket(ticket, null));
        when(credential.getQrValue()).thenReturn("signed-physical-qr");
        when(credentialRepository.findFirstByTicketIdAndStatusOrderByIssuedAtDesc(
                82L, TicketQrCredentialStatus.ACTIVE)).thenReturn(Optional.of(credential));
        when(qrImageService.pngBase64("signed-physical-qr"))
                .thenReturn("physical-qr-png");

        var response = service.issue(1L, request(
                CompensatoryDeliveryMethod.PHYSICAL_DEVICE,
                "RMM-TM-ST001-01", null), authentication());

        assertThat(response.status()).isEqualTo(CompensatoryIssuanceStatus.PROCESSING);
        assertThat(response.deliveryMethod()).isEqualTo(CompensatoryDeliveryMethod.PHYSICAL_DEVICE);
        assertThat(response.ticketCode()).isEqualTo("RMM-PHYSICAL-082");
        assertThat(response.qrPngBase64()).isEqualTo("physical-qr-png");
        assertThat(response.simulated()).isFalse();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(commandService).send(
                org.mockito.ArgumentMatchers.eq("RMM-TM-ST001-01"),
                org.mockito.ArgumentMatchers.eq(DeviceMqttCommandType.TICKET_ISSUE),
                payloadCaptor.capture(), any());
        assertThat(payloadCaptor.getValue())
                .containsEntry("issuanceKind", "COMPENSATORY")
                .containsKey("issuanceCode");
        assertThat(payloadCaptor.getValue().get("ticket")).isInstanceOfSatisfying(
                Map.class,
                issuedTicket -> assertThat(issuedTicket)
                        .containsEntry("ticketCode", "RMM-PHYSICAL-082")
                        .containsEntry("productType", "MULTI_TRIP")
                        .containsEntry("qrValue", "signed-physical-qr")
                        .containsEntry("qrPngBase64", "physical-qr-png")
                        .containsKey("linkingCode"));
        verify(eventService).registerRequested(any(), any());
        verify(eventService, never()).registerCompleted(any(), any());
    }

    @Test
    void shouldCompleteAnUnmonitoredPhysicalIssuanceWithoutCreatingATicket() {
        Device device = mock(Device.class);
        Station station = mock(Station.class);
        when(device.getCode()).thenReturn("TM-ST001-02");
        when(device.getName()).thenReturn("Máquina simulada");
        when(device.getType()).thenReturn(DeviceType.TICKET_MACHINE);
        when(device.getStatus()).thenReturn(DeviceStatus.ONLINE);
        when(device.isMqttManaged()).thenReturn(false);
        when(device.getStation()).thenReturn(station);
        when(station.getCode()).thenReturn("ST001");
        when(station.getName()).thenReturn("Aeropuerto");
        when(deviceRepository.findByCodeAndActiveTrue("TM-ST001-02")).thenReturn(Optional.of(device));

        var response = service.issue(1L, request(
                CompensatoryDeliveryMethod.PHYSICAL_DEVICE, "TM-ST001-02", null), authentication());

        assertThat(response.status()).isEqualTo(CompensatoryIssuanceStatus.COMPLETED);
        assertThat(response.simulated()).isTrue();
        assertThat(response.ticketCode()).isNull();
        assertThat(response.qrPngBase64()).isNull();
        verify(ticketIssuanceService, never()).issuePhysical(any(), any(), any(), any(), any());
        verify(commandService, never()).send(any(), any(), any(), any());
        verify(eventService).registerRequested(any(), any());
        verify(eventService).registerCompleted(any(), any());
    }

    private CompensatoryTicketIssuanceRequest request(
            CompensatoryDeliveryMethod method,
            String deviceCode,
            String passengerPublicId
    ) {
        return new CompensatoryTicketIssuanceRequest(
                deviceCode, "Compensación administrativa", null, null,
                10, null, null, method, passengerPublicId
        );
    }

    private UsernamePasswordAuthenticationToken authentication() {
        OperatorPrincipal principal = new OperatorPrincipal(
                7L, "admin", "admin@example.com", "Admin", "RMM", OperatorRole.ADMINISTRATOR
        );
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }
}
