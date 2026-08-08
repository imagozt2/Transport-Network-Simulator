package com.transport.simulator.ticketing.qr;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"alg", "kid", "typ"})
public record TicketQrProtectedHeader(
        @JsonProperty("alg") String algorithm,
        @JsonProperty("kid") String keyId,
        @JsonProperty("typ") String type
) {
    public TicketQrProtectedHeader {
        if (!TicketQrContract.JWS_ALGORITHM.equals(algorithm)) {
            throw new IllegalArgumentException("Unsupported ticket QR signature algorithm");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("Ticket QR signing key id is required");
        }
        if (!TicketQrContract.JWS_TYPE.equals(type)) {
            throw new IllegalArgumentException("Unsupported ticket QR token type");
        }
    }
}
