package com.transport.simulator.dto.response.incident;

import com.transport.simulator.entity.IncidentComment;
import java.time.LocalDateTime;

public record IncidentCommentResponse(
        Long id,
        String text,
        IncidentOperatorResponse author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static IncidentCommentResponse from(IncidentComment comment) {
        return new IncidentCommentResponse(
                comment.getId(),
                comment.getText(),
                IncidentOperatorResponse.from(comment.getAuthor()),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
