package com.transport.simulator.ticketing.qr;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class TicketQrPayloadCodec {

    private final ObjectMapper objectMapper;

    public TicketQrPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] encode(TicketQrPayload payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Ticket QR payload could not be encoded", exception);
        }
    }

    public String encodeToString(TicketQrPayload payload) {
        return new String(encode(payload), StandardCharsets.UTF_8);
    }

    public TicketQrPayload decode(byte[] payload) {
        try {
            return objectMapper.readValue(payload, TicketQrPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid ticket QR payload", exception);
        }
    }
}
