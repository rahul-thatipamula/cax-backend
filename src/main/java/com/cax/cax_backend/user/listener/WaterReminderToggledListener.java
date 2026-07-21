package com.cax.cax_backend.user.listener;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.model.CollegeWaterMilestoneState;
import com.cax.cax_backend.notification.repository.CollegeWaterMilestoneStateRepository;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.user.event.WaterReminderToggledEvent;
import com.cax.cax_backend.user.model.CollegeDetails;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * See docs/engagement/WATER_REMINDER_ENGAGEMENT_2026-07-21.md.
 * Rank 2: personal toggle confirmation. Rank 1: college milestone broadcast.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaterReminderToggledListener {

    // Milestone ladder: 10, 25, 50, 100, 250, then every +250 after that.
    private static final int[] MILESTONE_STEPS = {10, 25, 50, 100, 250};

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final CollegeWaterMilestoneStateRepository milestoneStateRepository;

    @Async("taskExecutor")
    @EventListener
    public void handleWaterReminderToggled(WaterReminderToggledEvent event) {
        User user = event.getUser();
        boolean subscribed = event.isSubscribed();

        // Rank 2: personal confirmation
        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", subscribed ? "WATER_REMINDER_ENABLED" : "WATER_REMINDER_DISABLED");

            notificationService.createNotification(
                    user.getUserId(),
                    subscribed ? "Water Reminders On 💧" : "Water Reminders Off",
                    subscribed
                            ? "We'll nudge you through the day to stay hydrated."
                            : "You won't receive water reminders anymore. Turn them back on anytime from settings.",
                    NotificationType.SYSTEM,
                    data
            );
        } catch (Exception e) {
            log.error("Failed to process WaterReminderToggledEvent for user {}: {}", user.getUserId(), e.getMessage(), e);
        }

        // Rank 1: college milestone broadcast — only relevant when someone just opted in
        if (subscribed) {
            try {
                checkAndBroadcastCollegeMilestone(user);
            } catch (Exception e) {
                log.error("Failed to check water milestone for college of user {}: {}", user.getUserId(), e.getMessage(), e);
            }
        }
    }

    private void checkAndBroadcastCollegeMilestone(User actor) {
        CollegeDetails collegeDetails = actor.getCollegeDetails();
        if (collegeDetails == null || collegeDetails.getCollegeId() == null || collegeDetails.getCollegeId().isBlank()) {
            return;
        }
        String collegeId = collegeDetails.getCollegeId();
        String collegeName = collegeDetails.getCollegeName();

        long subscribedCount = userRepository.countWaterReminderSubscribedByCollegeId(collegeId);

        CollegeWaterMilestoneState state = milestoneStateRepository.findByCollegeId(collegeId)
                .orElseGet(() -> CollegeWaterMilestoneState.builder().collegeId(collegeId).lastMilestoneNotified(0).build());

        int milestone = highestMilestoneReached(subscribedCount);
        if (milestone <= state.getLastMilestoneNotified()) {
            return; // no new milestone crossed
        }

        state.setLastMilestoneNotified(milestone);
        state.setUpdatedAt(Instant.now());
        milestoneStateRepository.save(state);

        Map<String, String> data = new HashMap<>();
        data.put("type", "WATER_COLLEGE_MILESTONE");
        data.put("collegeId", collegeId);

        String title = "💧 " + collegeName + " is on it";
        String body = milestone + "+ people at " + collegeName + " just turned on water reminders. Join them?";

        broadcastToCollegeExcluding(collegeId, actor.getUserId(), title, body, data);
    }

    private int highestMilestoneReached(long count) {
        int highest = 0;
        for (int step : MILESTONE_STEPS) {
            if (count >= step) {
                highest = step;
            }
        }
        if (count > 250) {
            // Nearest +250 step reached above the 250 base, e.g. 260->250, 500->500, 501->500.
            highest = 250 + (int) (((count - 250) / 250) * 250);
        }
        return highest;
    }

    /**
     * Fans out a college-scoped notification, excluding one user (the actor whose
     * action triggered it) and respecting the shared daily push budget
     * (User.lastNotificationSentAt, same field FeedNotificationScheduler uses) so
     * this can't stack with other daily broadcasts for the same recipient.
     */
    private void broadcastToCollegeExcluding(String collegeId, String excludeUserId, String title, String body, Map<String, String> data) {
        List<User> eligible = userRepository.findNotificationEligibleUsersByCollegeId(collegeId);
        Instant oneDayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        int sent = 0, skipped = 0;

        for (User recipient : eligible) {
            if (recipient.getUserId().equals(excludeUserId)) {
                continue;
            }
            if (recipient.getLastNotificationSentAt() != null && recipient.getLastNotificationSentAt().isAfter(oneDayAgo)) {
                skipped++;
                continue;
            }
            try {
                notificationService.createNotification(recipient.getUserId(), title, body, NotificationType.SYSTEM, data);
                recipient.setLastNotificationSentAt(Instant.now());
                userRepository.save(recipient);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send water milestone notification to user {}: {}", recipient.getUserId(), e.getMessage());
            }
        }
        log.info("Water milestone broadcast for college {} complete. Sent={}, SkippedBudget={}", collegeId, sent, skipped);
    }
}
