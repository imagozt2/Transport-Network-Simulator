package com.transport.simulator.service.model;

import com.transport.simulator.entity.Purchase;
import com.transport.simulator.entity.Ticket;

public record TicketMachineRechargeResult(
        Purchase purchase,
        Ticket ticket,
        String qrValue
) {
}
