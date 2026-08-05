package com.transport.simulator.dto.request.incident;

import jakarta.validation.constraints.NotBlank;

public record IncidentCommentCreateRequest(@NotBlank String text) {
}
