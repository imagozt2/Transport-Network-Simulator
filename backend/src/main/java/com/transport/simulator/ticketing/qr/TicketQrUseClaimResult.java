package com.transport.simulator.ticketing.qr;

import com.transport.simulator.entity.TicketQrUseClaim;

public record TicketQrUseClaimResult(
        TicketQrUseClaimOutcome outcome,
        TicketQrUseClaim claim
) {
}
