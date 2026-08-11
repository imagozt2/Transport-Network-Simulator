package com.transport.simulator.service;

import com.transport.simulator.dto.request.passengerticket.PassengerTicketLinkRequest;
import com.transport.simulator.dto.response.passengerticket.PassengerTicketDetailResponse;
import com.transport.simulator.entity.PassengerAccount;
import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketOperation;
import com.transport.simulator.entity.TicketSupport;
import com.transport.simulator.enums.PassengerAccountStatus;
import com.transport.simulator.enums.TicketOperationSource;
import com.transport.simulator.enums.TicketOperationType;
import com.transport.simulator.enums.TicketStatus;
import com.transport.simulator.enums.TicketSupportStatus;
import com.transport.simulator.enums.TicketSupportType;
import com.transport.simulator.repository.TicketOperationRepository;
import com.transport.simulator.service.model.TicketSnapshot;
import com.transport.simulator.ticketing.qr.TicketQrVerificationException;
import com.transport.simulator.ticketing.qr.TicketQrVerifier;
import com.transport.simulator.ticketing.qr.VerifiedTicketQr;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PassengerTicketLinkService {

    private final PassengerResourceAccessService accessService;
    private final PassengerTicketQueryService ticketQueryService;
    private final TicketQrVerifier qrVerifier;
    private final TicketOperationRepository operationRepository;
    private final TicketOperationRegistrationService operationRegistrationService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public PassengerTicketLinkService(
            PassengerResourceAccessService accessService,
            PassengerTicketQueryService ticketQueryService,
            TicketQrVerifier qrVerifier,
            TicketOperationRepository operationRepository,
            TicketOperationRegistrationService operationRegistrationService,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.accessService = accessService;
        this.ticketQueryService = ticketQueryService;
        this.qrVerifier = qrVerifier;
        this.operationRepository = operationRepository;
        this.operationRegistrationService = operationRegistrationService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public PassengerTicketDetailResponse link(
            String idempotencyKey,
            PassengerTicketLinkRequest request,
            Authentication authentication
    ) {
        PassengerAccount passenger = accessService.currentAccount(authentication);
        if (passenger.getStatus() != PassengerAccountStatus.ACTIVE) {
            throw conflict("PASSENGER_ACCOUNT_NOT_ACTIVE");
        }

        String normalizedLinkCode = normalizeLinkCode(request.linkCode());
        String referencePrefix = secureHash(requireIdempotencyKey(idempotencyKey)) + ":";
        String operationReference = referencePrefix + secureHash(
                request.qrValue().trim() + "\u0000" + normalizedLinkCode
        );
        TicketOperation previous = operationRepository
                .findFirstByTypeAndSourceAndExternalReferenceStartingWith(
                        TicketOperationType.SUPPORT_LINKED,
                        TicketOperationSource.RMM_APP,
                        referencePrefix
                )
                .orElse(null);
        if (previous != null) {
            if (!operationReference.equals(previous.getExternalReference())
                    || previous.getPassengerAccount() == null
                    || !previous.getPassengerAccount().getId().equals(passenger.getId())) {
                throw conflict("IDEMPOTENCY_KEY_REUSED");
            }
            return ticketQueryService.ticket(previous.getTicket().getCode(), authentication);
        }

        VerifiedTicketQr verified = verifyQr(request.qrValue());
        TicketSupport support = verified.credential().getSupport();
        Ticket ticket = verified.credential().getTicket();
        requireLinkable(ticket, support, passenger);
        requireValidLinkCode(support, normalizedLinkCode);

        LocalDateTime now = LocalDateTime.now(clock);
        TicketSnapshot before = TicketSnapshot.from(ticket);
        ticket.assignPassenger(passenger);
        support.linkToPassenger(passenger, now);
        operationRegistrationService.recordSupportLink(
                ticket, support, passenger, operationReference
        );
        // La vinculación cambia la titularidad, no los derechos tarifarios del billete.
        if (!before.equals(TicketSnapshot.from(ticket))) {
            throw new IllegalStateException("Ticket linking cannot modify ticket rights");
        }
        return ticketQueryService.ticket(ticket.getCode(), authentication);
    }

    private VerifiedTicketQr verifyQr(String value) {
        try {
            return qrVerifier.verify(value);
        } catch (TicketQrVerificationException exception) {
            throw invalidLinkProof();
        }
    }

    private void requireLinkable(
            Ticket ticket,
            TicketSupport support,
            PassengerAccount passenger
    ) {
        if (support.getType() != TicketSupportType.PHYSICAL
                || support.getStatus() != TicketSupportStatus.ACTIVE
                || !support.getTicket().getId().equals(ticket.getId())
                || (ticket.getStatus() != TicketStatus.ACTIVE
                    && ticket.getStatus() != TicketStatus.EXHAUSTED
                    && ticket.getStatus() != TicketStatus.EXPIRED)) {
            throw unprocessable("TICKET_NOT_LINKABLE");
        }
        PassengerAccount owner = ticket.getPassengerAccount() != null
                ? ticket.getPassengerAccount()
                : support.getPassengerAccount();
        if (owner != null) {
            if (owner.getId().equals(passenger.getId())) {
                throw conflict("TICKET_ALREADY_IN_WALLET");
            }
            throw conflict("TICKET_ALREADY_LINKED");
        }
    }

    private void requireValidLinkCode(TicketSupport support, String linkCode) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (support.getLinkingCodeHash() == null
                || support.getLinkingCodeExpiresAt() == null
                || now.isAfter(support.getLinkingCodeExpiresAt())
                || !passwordEncoder.matches(linkCode, support.getLinkingCodeHash())) {
            throw invalidLinkProof();
        }
    }

    private String requireIdempotencyKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.length() < 16 || key.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain between 16 and 150 characters"
            );
        }
        return key;
    }

    private String normalizeLinkCode(String value) {
        String code = value == null ? "" : value.trim()
                .replace("-", "")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
        if (code.length() < 4 || code.length() > 32) {
            throw invalidLinkProof();
        }
        return code;
    }

    private String secureHash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Ticket link reference could not be calculated", exception);
        }
    }

    private ResponseStatusException invalidLinkProof() {
        return unprocessable("INVALID_LINK_CODE");
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private ResponseStatusException unprocessable(String reason) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, reason);
    }
}
