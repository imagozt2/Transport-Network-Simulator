package com.transport.simulator.ticketing.qr;

import com.transport.simulator.entity.TicketSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TicketQrPayloadFactory {

    private final Clock clock;

    public TicketQrPayloadFactory(Clock clock) {
        this.clock = clock;
    }

    public TicketQrPayload createWithoutExpiry(TicketSupport support, UUID credentialId) {
        return create(support, credentialId, null);
    }

    public TicketQrPayload create(
            TicketSupport support,
            UUID credentialId,
            Duration technicalValidity
    ) {
        Objects.requireNonNull(support, "support is required");
        Objects.requireNonNull(credentialId, "credentialId is required");
        if (support.getTicket() == null) {
            throw new IllegalArgumentException("The ticket support is not associated with a ticket");
        }
        if (technicalValidity != null && (technicalValidity.isZero() || technicalValidity.isNegative())) {
            throw new IllegalArgumentException("technicalValidity must be positive when supplied");
        }

        Instant issuedAt = clock.instant();
        Long expiresAt = technicalValidity == null
                ? null
                : issuedAt.plus(technicalValidity).getEpochSecond();
        return new TicketQrPayload(
                TicketQrContract.PAYLOAD_VERSION,
                TicketQrContract.ISSUER,
                TicketQrContract.AUDIENCE,
                credentialId,
                support.getTicket().getCode(),
                support.getType(),
                issuedAt.getEpochSecond(),
                expiresAt
        );
    }
}
