package com.cax.cax_backend.notification.scheduler;

import com.cax.cax_backend.college.model.College;
import com.cax.cax_backend.college.repository.CollegeRepository;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.model.UserWaterPeerNudgeState;
import com.cax.cax_backend.notification.repository.UserWaterPeerNudgeStateRepository;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weekly water-reminder peer nudge for non-adopters.
 * See docs/engagement/WATER_REMINDER_ENGAGEMENT_2026-07-21.md — Rank 3.
 *
 * Only nudges colleges that already have visible momentum (>=10 subscribers,
 * same base threshold as the Rank 1 milestone broadcast), and caps each user
 * at 2 lifetime nudges, 30+ days apart, sharing the same daily push budget
 * (User.lastNotificationSentAt) used by FeedNotificationScheduler and the
 * Rank 1 milestone broadcast so a user is never double-pushed the same day.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WaterReminderPeerNudgeScheduler {

    private static final int MOMENTUM_THRESHOLD = 10;
    private static final int MAX_LIFETIME_NUDGES = 2;
    private static final int RENUDGE_AFTER_DAYS = 30;

    private final CollegeRepository collegeRepository;
    private final UserRepository userRepository;
    private final UserWaterPeerNudgeStateRepository nudgeStateRepository;
    private final NotificationService notificationService;

    // Every Monday at 11:00 AM IST.
    @Scheduled(cron = "0 0 11 * * MON", zone = "Asia/Kolkata")
    public void sendWeeklyPeerNudges() {
        log.info("[WaterPeerNudge] Starting weekly sweep...");
        Instant oneDayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        Instant renudgeThreshold = Instant.now().minus(RENUDGE_AFTER_DAYS, ChronoUnit.DAYS);

        List<College> colleges = collegeRepository.findByIsActiveTrue();
        int totalSent = 0, totalSkipped = 0;

        for (College college : colleges) {
            String collegeId = college.getId();
            long subscribedCount = userRepository.countWaterReminderSubscribedByCollegeId(collegeId);
            if (subscribedCount < MOMENTUM_THRESHOLD) {
                continue; // not enough social proof yet to make the nudge credible
            }

            Map<String, String> data = new HashMap<>();
            data.put("type", "WATER_PEER_NUDGE");
            data.put("collegeId", collegeId);
            String title = "Most of your campus is hydrating 💧";
            String body = subscribedCount + " students at " + college.getCollegeName()
                    + " have water reminders on. Turn yours on in 1 tap.";

            List<User> candidates = userRepository.findNotificationEligibleUsersByCollegeId(collegeId);
            for (User candidate : candidates) {
                if (candidate.isWaterReminderSubscribed()) {
                    continue; // already adopted, not a nudge target
                }
                if (candidate.getLastNotificationSentAt() != null && candidate.getLastNotificationSentAt().isAfter(oneDayAgo)) {
                    totalSkipped++;
                    continue; // shared daily push budget already used today
                }

                UserWaterPeerNudgeState state = nudgeStateRepository.findByUserId(candidate.getUserId())
                        .orElseGet(() -> UserWaterPeerNudgeState.builder().userId(candidate.getUserId()).nudgeCount(0).build());

                if (state.getNudgeCount() >= MAX_LIFETIME_NUDGES) {
                    continue; // lifetime cap reached
                }
                if (state.getNudgeCount() > 0
                        && (state.getLastNudgeSentAt() == null || state.getLastNudgeSentAt().isAfter(renudgeThreshold))) {
                    continue; // too soon since the first nudge
                }

                try {
                    notificationService.createNotification(candidate.getUserId(), title, body, NotificationType.SYSTEM, data);

                    state.setNudgeCount(state.getNudgeCount() + 1);
                    state.setLastNudgeSentAt(Instant.now());
                    nudgeStateRepository.save(state);

                    candidate.setLastNotificationSentAt(Instant.now());
                    userRepository.save(candidate);
                    totalSent++;
                } catch (Exception e) {
                    log.error("[WaterPeerNudge] Failed to nudge user {}: {}", candidate.getUserId(), e.getMessage());
                }
            }
        }

        log.info("[WaterPeerNudge] Weekly sweep complete. Sent={}, SkippedBudget={}", totalSent, totalSkipped);
    }
}
