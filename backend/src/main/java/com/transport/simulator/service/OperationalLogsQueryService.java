package com.transport.simulator.service;

import com.transport.simulator.dto.response.operationallog.OperationalLogResponse;
import com.transport.simulator.dto.response.operationallog.OperationalLogsPageResponse;
import com.transport.simulator.entity.DeviceEventLog;
import com.transport.simulator.enums.DeviceEventType;
import com.transport.simulator.enums.LogOrigin;
import com.transport.simulator.enums.LogSeverity;
import com.transport.simulator.repository.DeviceEventLogRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class OperationalLogsQueryService {

    static final int DEFAULT_PAGE_SIZE = 25;
    static final int MAX_PAGE_SIZE = 100;

    private final DeviceEventLogRepository eventLogRepository;

    public OperationalLogsQueryService(DeviceEventLogRepository eventLogRepository) {
        this.eventLogRepository = eventLogRepository;
    }

    public OperationalLogsPageResponse getLogs(
            int page,
            int size,
            LogOrigin origin,
            LogSeverity severity,
            DeviceEventType eventType,
            String deviceCode,
            String stationCode,
            LocalDateTime occurredFrom,
            LocalDateTime occurredTo
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        validateDateRange(occurredFrom, occurredTo);

        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(
                        Sort.Order.desc("occurredAt"),
                        Sort.Order.desc("id")
                )
        );
        Page<DeviceEventLog> result = eventLogRepository.findFiltered(
                origin,
                severity,
                eventType,
                normalize(deviceCode),
                normalize(stationCode),
                occurredFrom,
                occurredTo,
                pageable
        );

        return new OperationalLogsPageResponse(
                result.getContent().stream().map(OperationalLogResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                result.isEmpty()
        );
    }

    private void validateDateRange(LocalDateTime occurredFrom, LocalDateTime occurredTo) {
        if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "occurredFrom must be earlier than or equal to occurredTo"
            );
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
