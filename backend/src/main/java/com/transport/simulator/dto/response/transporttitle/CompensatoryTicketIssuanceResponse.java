package com.transport.simulator.dto.response.transporttitle;

import com.transport.simulator.entity.CompensatoryTicketIssuance;
import com.transport.simulator.enums.CompensatoryIssuanceStatus;
import com.transport.simulator.enums.CompensatoryDeliveryMethod;
import com.transport.simulator.enums.TicketProductType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CompensatoryTicketIssuanceResponse(
        Long id,
        String code,
        CompensatoryIssuanceStatus status,
        boolean simulated,
        String ticketCode,
        String qrToken,
        String productCode,
        TicketProductType productType,
        CompensatoryDeliveryMethod deliveryMethod,
        String deviceCode,
        String deviceName,
        String stationCode,
        String stationName,
        String passengerPublicId,
        String passengerEmail,
        String operatorUsername,
        BigDecimal chargedAmount,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {
    public static CompensatoryTicketIssuanceResponse from(CompensatoryTicketIssuance issuance) {
        return new CompensatoryTicketIssuanceResponse(
                issuance.getId(), issuance.getCode(), issuance.getStatus(),
                issuance.getDeliveryMethod() == CompensatoryDeliveryMethod.PHYSICAL_DEVICE
                        && issuance.getIssuedTicket() == null,
                issuance.getIssuedTicket() == null ? null : issuance.getIssuedTicket().getCode(),
                issuance.getIssuedTicket() == null ? null : issuance.getIssuedTicket().getQrToken(),
                issuance.getProduct().getCode(), issuance.getProduct().getProductType(),
                issuance.getDeliveryMethod(),
                issuance.getTargetDevice() == null ? null : issuance.getTargetDevice().getCode(),
                issuance.getTargetDevice() == null ? null : issuance.getTargetDevice().getName(),
                issuance.getTargetDevice() == null ? null : issuance.getTargetDevice().getStation().getCode(),
                issuance.getTargetDevice() == null ? null : issuance.getTargetDevice().getStation().getName(),
                issuance.getRecipientPassenger() == null
                        ? null : issuance.getRecipientPassenger().getPublicId(),
                issuance.getRecipientPassenger() == null
                        ? null : issuance.getRecipientPassenger().getEmail(),
                issuance.getRequestedBy().getUsername(), BigDecimal.ZERO,
                issuance.getRequestedAt(), issuance.getCompletedAt()
        );
    }
}
