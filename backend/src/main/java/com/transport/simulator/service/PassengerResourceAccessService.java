package com.transport.simulator.service;

import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.Purchase;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketSupport;
import com.transport.simulator.repository.PassengerAccountRepository;
import com.transport.simulator.repository.PurchaseRepository;
import com.transport.simulator.repository.TicketRepository;
import com.transport.simulator.repository.TicketSupportRepository;
import com.transport.simulator.security.PassengerPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PassengerResourceAccessService {

    private final PassengerAccountRepository accountRepository;
    private final TicketRepository ticketRepository;
    private final TicketSupportRepository supportRepository;
    private final PurchaseRepository purchaseRepository;

    public PassengerResourceAccessService(
            PassengerAccountRepository accountRepository,
            TicketRepository ticketRepository,
            TicketSupportRepository supportRepository,
            PurchaseRepository purchaseRepository
    ) {
        this.accountRepository = accountRepository;
        this.ticketRepository = ticketRepository;
        this.supportRepository = supportRepository;
        this.purchaseRepository = purchaseRepository;
    }

    public PassengerPrincipal requirePassenger(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PassengerPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Passenger authentication is required");
        }
        return principal;
    }

    @Transactional(readOnly = true)
    public PassengerAccount currentAccount(Authentication authentication) {
        PassengerPrincipal principal = requirePassenger(authentication);
        return accountRepository.findById(principal.accountId())
                .filter(account -> account.getPublicId().equals(principal.publicId()))
                .orElseThrow(this::resourceNotFound);
    }

    @Transactional(readOnly = true)
    public Ticket ownedTicket(String ticketCode, Authentication authentication) {
        PassengerPrincipal principal = requirePassenger(authentication);
        return ticketRepository.findByCodeAndPassengerAccountId(
                normalizeCode(ticketCode), principal.accountId()
        ).orElseThrow(this::resourceNotFound);
    }

    @Transactional(readOnly = true)
    public TicketSupport ownedSupport(String supportCode, Authentication authentication) {
        PassengerPrincipal principal = requirePassenger(authentication);
        return supportRepository.findOwnedByCode(
                normalizeCode(supportCode), principal.accountId()
        ).orElseThrow(this::resourceNotFound);
    }

    @Transactional(readOnly = true)
    public Purchase ownedPurchase(String purchaseCode, Authentication authentication) {
        PassengerPrincipal principal = requirePassenger(authentication);
        return purchaseRepository.findByCodeAndPassengerAccountId(
                normalizeCode(purchaseCode), principal.accountId()
        ).orElseThrow(this::resourceNotFound);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw resourceNotFound();
        }
        return code.trim();
    }

    private ResponseStatusException resourceNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Passenger resource not found");
    }
}
