package com.transport.simulator.ticketing.qr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.transport.simulator.enums.TicketSupportType;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"ver", "iss", "aud", "jti", "ticket", "medium", "iat", "exp"})
public record TicketQrPayload(
        @JsonProperty("ver") int version,
        @JsonProperty("iss") String issuer,
        @JsonProperty("aud") String audience,
        @JsonProperty("jti") UUID credentialId,
        @JsonProperty("ticket") String ticketCode,
        @JsonProperty("medium") TicketSupportType medium,
        @JsonProperty("iat") long issuedAtEpochSecond,
        @JsonProperty("exp") Long expiresAtEpochSecond
) {

    private static final Pattern TICKET_CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9-]{0,79}$");

    public TicketQrPayload {
        if (version != TicketQrContract.PAYLOAD_VERSION) {
            throw new IllegalArgumentException("Unsupported ticket QR payload version");
        }
        if (!TicketQrContract.ISSUER.equals(issuer)) {
            throw new IllegalArgumentException("Invalid ticket QR issuer");
        }
        if (!TicketQrContract.AUDIENCE.equals(audience)) {
            throw new IllegalArgumentException("Invalid ticket QR audience");
        }
        Objects.requireNonNull(credentialId, "credentialId is required");
        Objects.requireNonNull(medium, "medium is required");
        if (ticketCode == null || !TICKET_CODE_PATTERN.matcher(ticketCode).matches()) {
            throw new IllegalArgumentException("Invalid public ticket code");
        }
        if (issuedAtEpochSecond < 0) {
            throw new IllegalArgumentException("issuedAtEpochSecond cannot be negative");
        }
        if (expiresAtEpochSecond != null && expiresAtEpochSecond < issuedAtEpochSecond) {
            throw new IllegalArgumentException("QR credential expiry cannot precede its issuance");
        }
    }

    public boolean hasTechnicalExpiry() {
        return expiresAtEpochSecond != null;
    }
}
