package com.transport.simulator.service;

import com.transport.simulator.entity.Ticket;
import com.transport.simulator.entity.TicketJourney;
import com.transport.simulator.enums.TicketJourneyStatus;
import com.transport.simulator.repository.TicketJourneyRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketJourneyAnomalyService {

    private final TicketJourneyRepository journeyRepository;
    private final Clock clock;
    private final Duration maximumOpenDuration;

    public TicketJourneyAnomalyService(
            TicketJourneyRepository journeyRepository,
            Clock clock,
            @Value("${app.ticketing.maximum-open-journey-duration:PT6H}")
            Duration maximumOpenDuration
    ) {
        this.journeyRepository = journeyRepository;
        this.clock = clock;
        if (maximumOpenDuration == null || maximumOpenDuration.isZero()
                || maximumOpenDuration.isNegative()) {
            throw new IllegalArgumentException("maximumOpenDuration must be positive");
        }
        this.maximumOpenDuration = maximumOpenDuration;
    }

    @Transactional
    public Optional<TicketJourney> forceCloseJourneyWithoutExit(Ticket ticket) {
        return journeyRepository.findFirstByTicketAndStatusOrderByOpenedAtDesc(
                        ticket, TicketJourneyStatus.OPEN)
                .map(journey -> {
                    journey.forceClose(LocalDateTime.now(clock));
                    return journeyRepository.save(journey);
                });
    }

    @Scheduled(fixedDelayString = "${app.ticketing.open-journey-review-interval-ms:60000}")
    @Transactional
    public int forceCloseExpiredOpenJourneys() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<TicketJourney> expired = journeyRepository.findAllByStatusAndOpenedAtBefore(
                TicketJourneyStatus.OPEN,
                now.minus(maximumOpenDuration)
        );
        expired.forEach(journey -> journey.forceClose(now));
        journeyRepository.saveAll(expired);
        return expired.size();
    }
}
