package com.cax.cax_backend.event.service;

import com.cax.cax_backend.event.model.Event;
import com.cax.cax_backend.event.model.EventParticipant;
import com.cax.cax_backend.event.repository.EventParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventAnalyticsService {

    private final EventParticipantRepository eventParticipantRepository;

    /**
     * Calculates the mathematical engagement score for a standard Event (Internal College or Global).
     * S_final = S_raw * D(t_created) * M_schedule(now, start, end, regEnd)
     */
    public double calculateEventEngagementScore(Event event) {
        if (event == null) return 0.0;

        Instant now = Instant.now();

        // 1. Raw Interaction Signals (Joined Participants, Base Priority)
        long joinedCount = 0;
        try {
            joinedCount = eventParticipantRepository.countByEventIdAndStatus(event.getId(), EventParticipant.ParticipantStatus.JOINED);
        } catch (Exception e) {
            log.error("Failed to count participants for event {}: {}", event.getId(), e.getMessage());
        }

        double rawScore;
        if (event.isGlobal()) {
            // Global Event Signal Weights
            rawScore = (joinedCount * 3.0) + 1.0;
        } else {
            // Internal College Event Signal Weights
            rawScore = (joinedCount * 2.0) + 1.0;
        }

        // 2. Recency Time-Decay Factor: D(t) = 1 / sqrt(1 + hoursOld / 24)
        Instant createdAt = event.getCreatedAt() != null ? event.getCreatedAt() : now;
        long hoursOld = Math.max(0, Duration.between(createdAt, now).toHours());
        double recencyDecay = 1.0 / Math.sqrt(1.0 + (hoursOld / 24.0));

        // 3. Schedule & Urgency Multiplier: M_schedule
        // Priority order: Urgent Registration (up to 2.5x) > Upcoming Events (2.0x) > Live Events (1.5x) > Standard (1.0x) > Closed (0.15x)
        double scheduleMultiplier = 1.0;

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

        return rawScore * recencyDecay * scheduleMultiplier;
    }
}
