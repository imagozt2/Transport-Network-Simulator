package com.transport.simulator.service;

import com.transport.simulator.dto.response.transporttitle.TransportTitleResponse;
import com.transport.simulator.dto.response.transporttitle.TransportTitleSummaryResponse;
import com.transport.simulator.dto.response.transporttitle.TransportTitlesResponse;
import com.transport.simulator.entity.TicketProduct;
import com.transport.simulator.enums.TicketProductType;
import com.transport.simulator.repository.TicketProductRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class TransportTitleQueryService {

    private static final String CURRENCY = "EUR";

    private final TicketProductRepository ticketProductRepository;

    public TransportTitleQueryService(TicketProductRepository ticketProductRepository) {
        this.ticketProductRepository = ticketProductRepository;
    }

    public TransportTitlesResponse getTitles(
            String search,
            TicketProductType type,
            Boolean active,
            Boolean rechargeable
    ) {
        List<TicketProduct> allTitles = ticketProductRepository.findAllByOrderByCodeAsc();
        String normalizedSearch = normalize(search);

        List<TransportTitleResponse> filteredTitles = allTitles.stream()
                .filter(title -> matchesSearch(title, normalizedSearch))
                .filter(title -> type == null || title.getProductType() == type)
                .filter(title -> active == null || title.isActive() == active)
                .filter(title -> rechargeable == null || title.isRechargeable() == rechargeable)
                .map(TransportTitleResponse::from)
                .toList();

        return new TransportTitlesResponse(
                CURRENCY,
                summarize(allTitles, filteredTitles.size()),
                filteredTitles
        );
    }

    public TransportTitleResponse getTitle(long titleId) {
        return ticketProductRepository.findById(titleId)
                .map(TransportTitleResponse::from)
                .orElseThrow(() -> notFound("id", Long.toString(titleId)));
    }

    public TransportTitleResponse getTitle(String code) {
        String normalizedCode = code == null ? null : code.trim();
        if (normalizedCode == null || normalizedCode.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Transport title code is required"
            );
        }

        return ticketProductRepository.findByCodeIgnoreCase(normalizedCode)
                .map(TransportTitleResponse::from)
                .orElseThrow(() -> notFound("code", normalizedCode));
    }

    private TransportTitleSummaryResponse summarize(
            List<TicketProduct> allTitles,
            long filteredTitles
    ) {
        Map<TicketProductType, Long> byType = new EnumMap<>(TicketProductType.class);
        for (TicketProductType type : TicketProductType.values()) {
            byType.put(type, 0L);
        }

        long activeTitles = 0;
        for (TicketProduct title : allTitles) {
            byType.compute(title.getProductType(), (ignored, count) -> count + 1);
            if (title.isActive()) {
                activeTitles++;
            }
        }

        return new TransportTitleSummaryResponse(
                allTitles.size(),
                filteredTitles,
                activeTitles,
                allTitles.size() - activeTitles,
                byType
        );
    }

    private boolean matchesSearch(TicketProduct title, String search) {
        if (search == null) {
            return true;
        }

        return contains(title.getCode(), search)
                || contains(title.getName(), search)
                || contains(title.getDescription(), search);
    }

    private boolean contains(String value, String search) {
        String normalizedValue = normalize(value);
        return normalizedValue != null && normalizedValue.contains(search);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException notFound(String field, String value) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Transport title not found with " + field + ": " + value
        );
    }
}
