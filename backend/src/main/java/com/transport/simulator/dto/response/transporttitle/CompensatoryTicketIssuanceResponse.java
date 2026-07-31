package com.transport.simulator.dto.response.transporttitle;

import com.transport.simulator.entity.CompensatoryTicketIssuance;
import com.transport.simulator.enums.CompensatoryIssuanceStatus;
import com.transport.simulator.enums.TicketProductType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CompensatoryTicketIssuanceResponse(
        Long id,
        String code,
        CompensatoryIssuanceStatus status,
        String ticketCode,
        String qrToken,
        String productCode,
        TicketProductType productType,
        String deviceCode,
        String deviceName,
        String stationCode,
        String stationName,
        String operatorUsername,
        BigDecimal chargedAmount,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {
    public static CompensatoryTicketIssuanceResponse from(CompensatoryTicketIssuance issuance) {
        return new CompensatoryTicketIssuanceResponse(
                issuance.getId(), issuance.getCode(), issuance.getStatus(),
                issuance.getIssuedTicket().getCode(), issuance.getIssuedTicket().getQrToken(),
                issuance.getProduct().getCode(), issuance.getProduct().getProductType(),
                issuance.getTargetDevice().getCode(), issuance.getTargetDevice().getName(),
                issuance.getTargetDevice().getStation().getCode(),
                issuance.getTargetDevice().getStation().getName(),
                issuance.getRequestedBy().getUsername(), BigDecimal.ZERO,
                issuance.getRequestedAt(), issuance.getCompletedAt()
        );
    }
}
