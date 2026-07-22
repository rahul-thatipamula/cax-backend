package com.cax.cax_backend.bulletinevent.service;

import com.cax.cax_backend.bulletinevent.model.BulletinEvent;
import com.cax.cax_backend.bulletinevent.model.BulletinEventAnalytics;
import com.cax.cax_backend.bulletinevent.model.BulletinEventScore;
import com.cax.cax_backend.bulletinevent.repository.BulletinEventAnalyticsRepository;
import com.cax.cax_backend.bulletinevent.repository.BulletinEventRepository;
import com.cax.cax_backend.bulletinevent.repository.BulletinEventScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulletinEventAnalyticsService {

    private final BulletinEventAnalyticsRepository analyticsRepository;
    private final BulletinEventScoreRepository scoreRepository;
    private final BulletinEventRepository bulletinEventRepository;

    /** Initializes analytics record for a newly created Bulletin Event. */
    public BulletinEventAnalytics initializeAnalytics(String bulletinEventId) {
        Instant now = Instant.now();
        BulletinEventAnalytics analytics = BulletinEventAnalytics.builder()
                .bulletinEventId(bulletinEventId)
                .webImpressionsCount(0)
                .webAppImpressionsCount(0)
                .webDetailViewsCount(0)
                .webAppViewsCount(0)
                .appViewsCount(0)
                .externalLinkClicksCount(0)
                .engagementScore(1.0)
                .lastCalculatedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        BulletinEventAnalytics saved = analyticsRepository.save(analytics);
        recalculateScoreForEvent(bulletinEventId);
        return saved;
    }

    /** Hourly background task: refreshes scores for all active global bulletins so time-decay and schedule urgency shifts update automatically. */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 3600000)
    public void recalculateAllActiveScoresHourly() {
        try {
            List<BulletinEvent> activeEvents = bulletinEventRepository.findActiveGlobal();
            for (BulletinEvent event : activeEvents) {
                recalculateScoreForEvent(event.getId());
            }
            log.info("Hourly background bulletin event score recalculation complete for {} events.", activeEvents.size());
        } catch (Exception e) {
            log.error("Failed hourly bulletin event score recalculation: {}", e.getMessage());
        }
    }

    /** Asynchronously records batch impressions across Web or Web App. */
    @Async("taskExecutor")
    public void recordImpressions(List<String> bulletinEventIds, String source) {
        if (bulletinEventIds == null || bulletinEventIds.isEmpty()) return;

        boolean isWebApp = "WEB_APP".equalsIgnoreCase(source);

        for (String id : bulletinEventIds) {
            if (id == null || id.isBlank()) continue;
            try {
                BulletinEventAnalytics analytics = getOrCreateAnalytics(id);
                if (isWebApp) {
                    analytics.setWebAppImpressionsCount(analytics.getWebAppImpressionsCount() + 1);
                } else {
                    analytics.setWebImpressionsCount(analytics.getWebImpressionsCount() + 1);
                }
                analytics.setUpdatedAt(Instant.now());
                analyticsRepository.save(analytics);
                recalculateScore(analytics);
            } catch (Exception e) {
                log.error("Failed to record impression for bulletin event {}: {}", id, e.getMessage());
            }
        }
    }

    /** Asynchronously records a detail screen view across Web, Web App, or Mobile. */
    @Async("taskExecutor")
    public void recordDetailView(String bulletinEventId, String source) {
        if (bulletinEventId == null || bulletinEventId.isBlank()) return;

        try {
            BulletinEventAnalytics analytics = getOrCreateAnalytics(bulletinEventId);
            if ("WEB_APP".equalsIgnoreCase(source)) {
                analytics.setWebAppViewsCount(analytics.getWebAppViewsCount() + 1);
            } else if ("MOBILE_APP".equalsIgnoreCase(source)) {
                analytics.setAppViewsCount(analytics.getAppViewsCount() + 1);
            } else {
                analytics.setWebDetailViewsCount(analytics.getWebDetailViewsCount() + 1);
            }
            analytics.setUpdatedAt(Instant.now());
            analyticsRepository.save(analytics);
            recalculateScore(analytics);
        } catch (Exception e) {
            log.error("Failed to record detail view for bulletin event {}: {}", bulletinEventId, e.getMessage());
        }
    }

    /** Asynchronously records external link ('Apply / Register Now') CTA clicks. */
    @Async("taskExecutor")
    public void recordExternalLinkClick(String bulletinEventId, String source) {
        if (bulletinEventId == null || bulletinEventId.isBlank()) return;

        try {
            BulletinEventAnalytics analytics = getOrCreateAnalytics(bulletinEventId);
            analytics.setExternalLinkClicksCount(analytics.getExternalLinkClicksCount() + 1);
            analytics.setUpdatedAt(Instant.now());
            analyticsRepository.save(analytics);
            recalculateScore(analytics);
        } catch (Exception e) {
            log.error("Failed to record external link click for bulletin event {}: {}", bulletinEventId, e.getMessage());
        }
    }

    public Optional<BulletinEventAnalytics> getAnalyticsByEventId(String bulletinEventId) {
        return analyticsRepository.findByBulletinEventId(bulletinEventId);
    }

    private BulletinEventAnalytics getOrCreateAnalytics(String bulletinEventId) {
        return analyticsRepository.findByBulletinEventId(bulletinEventId)
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    return BulletinEventAnalytics.builder()
                            .bulletinEventId(bulletinEventId)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                });
    }

    public double recalculateScoreForEvent(String bulletinEventId) {
        BulletinEventAnalytics analytics = getOrCreateAnalytics(bulletinEventId);
        return recalculateScore(analytics);
    }

    /**
     * Mathematical Engagement & Prioritization Score Engine.
     * S_final = S_raw * D(t_created) * M_schedule(now, start, end, regEnd)
     */
    public double recalculateScore(BulletinEventAnalytics analytics) {
        BulletinEvent event = bulletinEventRepository.findById(analytics.getBulletinEventId()).orElse(null);
        Instant now = Instant.now();

        // 1. Raw Interaction Score (Weighted Signals)
        double rawScore = (analytics.getWebImpressionsCount() * 1.0)
                + (analytics.getWebAppImpressionsCount() * 1.5)
                + (analytics.getWebDetailViewsCount() * 5.0)
                + (analytics.getWebAppViewsCount() * 5.0)
                + (analytics.getAppViewsCount() * 5.0)
                + (analytics.getExternalLinkClicksCount() * 15.0)
                + 1.0; // Base value 1.0 ensures new events have schedule priority

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

        // Final Score Calculation
        double finalScore = rawScore * recencyDecay * scheduleMultiplier;

        analytics.setEngagementScore(finalScore);
        analytics.setLastCalculatedAt(now);
        analytics.setUpdatedAt(now);
        analyticsRepository.save(analytics);

        // Synchronize with BulletinEventScore table for ranking compatibility
        syncScoreRepository(analytics.getBulletinEventId(), finalScore, now);

        return finalScore;
    }

    private void syncScoreRepository(String bulletinEventId, double score, Instant now) {
        try {
            BulletinEventScore bulletinScore = scoreRepository.findByBulletinEventId(bulletinEventId)
                    .orElseGet(() -> BulletinEventScore.builder()
                            .bulletinEventId(bulletinEventId)
                            .createdAt(now)
                            .build());

            bulletinScore.setScore(score);
            bulletinScore.setUpdatedAt(now);
            scoreRepository.save(bulletinScore);
        } catch (Exception e) {
            log.error("Failed to sync score repository for event {}: {}", bulletinEventId, e.getMessage());
        }
    }
}
