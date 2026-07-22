package com.cax.cax_backend.event.service;

import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventAnalytics;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.repository.EventAnalyticsRepository;
import com.cax.cax_backend.event.repository.EventParticipantRepository;
import com.cax.cax_backend.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventAnalyticsService {

    private final EventAnalyticsRepository analyticsRepository;
    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;

    /** Initializes analytics record for a newly created Event. */
    public EventAnalytics initializeAnalytics(Event event) {
        if (event == null) return null;
        Instant now = Instant.now();
        EventAnalytics analytics = EventAnalytics.builder()
                .eventId(event.getId())
                .collegeId(event.getCollegeId())
                .isGlobal(event.isGlobal())
                .detailViewsCount(0)
                .joinedCount(0)
                .sharesCount(0)
                .externalClicksCount(0)
                .internalScore(1.0)
                .globalScore(1.0)
                .lastCalculatedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        EventAnalytics saved = analyticsRepository.save(analytics);
        recalculateScore(saved, event);
        return saved;
    }

    /** Asynchronously records an event detail screen view. */
    @Async("taskExecutor")
    public void recordEventView(String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        try {
            EventAnalytics analytics = getOrCreateAnalytics(eventId);
            analytics.setDetailViewsCount(analytics.getDetailViewsCount() + 1);
            analytics.setUpdatedAt(Instant.now());
            analyticsRepository.save(analytics);
            recalculateScore(analytics, null);
        } catch (Exception e) {
            log.error("Failed to record event view for event {}: {}", eventId, e.getMessage());
        }
    }

    /** Asynchronously records a participant join. */
    @Async("taskExecutor")
    public void recordEventJoin(String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        try {
            EventAnalytics analytics = getOrCreateAnalytics(eventId);
            analytics.setJoinedCount(analytics.getJoinedCount() + 1);
            analytics.setUpdatedAt(Instant.now());
            analyticsRepository.save(analytics);
            recalculateScore(analytics, null);
        } catch (Exception e) {
            log.error("Failed to record event join for event {}: {}", eventId, e.getMessage());
        }
    }

    /** Asynchronously records an event share. */
    @Async("taskExecutor")
    public void recordEventShare(String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        try {
            EventAnalytics analytics = getOrCreateAnalytics(eventId);
            analytics.setSharesCount(analytics.getSharesCount() + 1);
            analytics.setUpdatedAt(Instant.now());
            analyticsRepository.save(analytics);
            recalculateScore(analytics, null);
        } catch (Exception e) {
            log.error("Failed to record event share for event {}: {}", eventId, e.getMessage());
        }
    }

    /** Asynchronously records an external link click. */
    @Async("taskExecutor")
    public void recordEventClick(String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        try {
            EventAnalytics analytics = getOrCreateAnalytics(eventId);
            analytics.setExternalClicksCount(analytics.getExternalClicksCount() + 1);
            analytics.setUpdatedAt(Instant.now());
            analyticsRepository.save(analytics);
            recalculateScore(analytics, null);
        } catch (Exception e) {
            log.error("Failed to record event click for event {}: {}", eventId, e.getMessage());
        }
    }

    public Optional<EventAnalytics> getAnalyticsByEventId(String eventId) {
        return analyticsRepository.findByEventId(eventId);
    }

    private EventAnalytics getOrCreateAnalytics(String eventId) {
        return analyticsRepository.findByEventId(eventId)
                .orElseGet(() -> {
                    Event event = eventRepository.findById(eventId).orElse(null);
                    Instant now = Instant.now();
                    return EventAnalytics.builder()
                            .eventId(eventId)
                            .collegeId(event != null ? event.getCollegeId() : null)
                            .isGlobal(event != null && event.isGlobal())
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                });
    }

    /** Hourly background task: refreshes scores for all events so time-decay and schedule urgency update automatically. */
    @Scheduled(fixedRate = 3600000)
    public void recalculateAllScoresHourly() {
        try {
            List<EventAnalytics> allAnalytics = analyticsRepository.findAll();
            for (EventAnalytics analytics : allAnalytics) {
                recalculateScore(analytics, null);
            }
            log.info("Hourly background event analytics score recalculation complete for {} events.", allAnalytics.size());
        } catch (Exception e) {
            log.error("Failed hourly event analytics score recalculation: {}", e.getMessage());
        }
    }

    /**
     * Calculates the mathematical engagement score for a standard Event (Internal College vs Global).
     * Strictly isolates internal college events to their respective college benchmark.
     */
    public double calculateEventEngagementScore(Event event) {
        if (event == null) return 0.0;
        EventAnalytics analytics = getOrCreateAnalytics(event.getId());
        return recalculateScore(analytics, event);
    }

    /**
     * Core Mathematical Score Engine with Strict College Isolation.
     */
    public double recalculateScore(EventAnalytics analytics, Event eventObj) {
        Event event = eventObj != null ? eventObj : eventRepository.findById(analytics.getEventId()).orElse(null);
        Instant now = Instant.now();

        long joinedCount = analytics.getJoinedCount();
        if (event != null && joinedCount == 0) {
            try {
                joinedCount = eventParticipantRepository.countByEventId(event.getId());
                analytics.setJoinedCount(joinedCount);
            } catch (Exception ignored) {}
        }

        // 1. Raw Interaction Score per event category
        double rawScore;
        boolean isGlobal = event != null ? event.isGlobal() : analytics.isGlobal();

        if (isGlobal) {
            rawScore = (joinedCount * 15.0)
                    + (analytics.getDetailViewsCount() * 4.0)
                    + (analytics.getSharesCount() * 6.0)
                    + (analytics.getExternalClicksCount() * 10.0)
                    + 1.0;
        } else {
            // Internal College Event Weights
            rawScore = (joinedCount * 10.0)
                    + (analytics.getDetailViewsCount() * 3.0)
                    + (analytics.getSharesCount() * 5.0)
                    + (analytics.getExternalClicksCount() * 8.0)
                    + 1.0;
        }

        // 2. Recency Time-Decay Factor: D(t) = 1 / sqrt(1 + hoursOld / 24)
        Instant createdAt = (event != null && event.getCreatedAt() != null) ? event.getCreatedAt() : analytics.getCreatedAt();
        if (createdAt == null) createdAt = now;
        long hoursOld = Math.max(0, Duration.between(createdAt, now).toHours());
        double recencyDecay = 1.0 / Math.sqrt(1.0 + (hoursOld / 24.0));

        // 3. Schedule & Urgency Multiplier: M_schedule
        // Priority order: Urgent Registration (up to 2.5x) > Upcoming Events (2.0x) > Live Events (1.5x) > Standard (1.0x) > Closed (0.15x)
        double scheduleMultiplier = 1.0;

        if (event != null) {
            Instant regEnd = event.getRegistrationEndDate();
            Instant start = event.getEventStartDate();
            Instant end = event.getEventEndDate();

            boolean isEnded = (end != null && now.isAfter(end)) || (regEnd != null && now.isAfter(regEnd));

            if (isEnded) {
                scheduleMultiplier = 0.15; // De-prioritize closed/ended events
            } else if (regEnd != null && now.isBefore(regEnd)) {
                long hoursToReg = Duration.between(now, regEnd).toHours();
                if (hoursToReg >= 0 && hoursToReg <= 48) {
                    // Urgent Registration Closing Boost (up to 2.5x)
                    scheduleMultiplier = 1.0 + (1.5 / (1.0 + (hoursToReg / 12.0)));
                } else if (start != null && now.isBefore(start)) {
                    long hoursToStart = Duration.between(now, start).toHours();
                    if (hoursToStart >= 0 && hoursToStart <= 168) {
                        scheduleMultiplier = 2.0; // Upcoming events prioritized ahead of live!
                    }
                } else if (start != null && end != null && !now.isBefore(start) && now.isBefore(end)) {
                    scheduleMultiplier = 1.5; // Live events
                }
            } else if (start != null && now.isBefore(start)) {
                long hoursToStart = Duration.between(now, start).toHours();
                if (hoursToStart >= 0 && hoursToStart <= 168) {
                    scheduleMultiplier = 2.0; // Upcoming events prioritized ahead of live!
                }
            } else if (start != null && end != null && !now.isBefore(start) && now.isBefore(end)) {
                scheduleMultiplier = 1.5; // Live events
            }
        }

        double calculatedScore = rawScore * recencyDecay * scheduleMultiplier;

        if (isGlobal) {
            analytics.setGlobalScore(calculatedScore);
        } else {
            // Strict College Isolation: benchmark against college's baseline
            analytics.setInternalScore(calculatedScore);
        }

        analytics.setLastCalculatedAt(now);
        analytics.setUpdatedAt(now);
        analyticsRepository.save(analytics);

        return calculatedScore;
    }
}
