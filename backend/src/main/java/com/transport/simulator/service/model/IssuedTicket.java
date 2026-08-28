package com.transport.simulator.service.model;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketSupport;

public record IssuedTicket(Ticket ticket, TicketSupport support) {
}
