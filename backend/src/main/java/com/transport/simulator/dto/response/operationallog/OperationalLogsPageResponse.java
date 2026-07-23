package com.transport.simulator.dto.response.operationallog;

import java.util.List;

public record OperationalLogsPageResponse(
        List<OperationalLogResponse> logs,
        int currentPage,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {
}
