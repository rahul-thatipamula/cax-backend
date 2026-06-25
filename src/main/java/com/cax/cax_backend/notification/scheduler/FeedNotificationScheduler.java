package com.cax.cax_backend.notification.scheduler;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.model.ThoughtEngagementScore;
import com.cax.cax_backend.thought.repository.ThoughtEngagementScoreRepository;
import com.cax.cax_backend.thought.repository.ThoughtRepository;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class FeedNotificationScheduler {

    private static final int TOP_THOUGHTS_COUNT = 5;

    private final UserRepository userRepository;
    private final ThoughtRepository thoughtRepository;
    private final ThoughtEngagementScoreRepository scoreRepository;
    private final NotificationService notificationService;

    /**
     * Runs every day at 1 PM server time.
     *
     * Picks the top {@value TOP_THOUGHTS_COUNT} globally trending thoughts
     * and sends a single push notification to every eligible user.
     * Thoughts are not college-scoped — everyone sees the same digest.
     *
     * One notification per user per day max (enforced via lastNotificationSentAt).
     */
    @Scheduled(cron = "0 0 13 * * *")
    public void sendDailyTrendingDigest() {
        log.info("[FeedScheduler] Starting daily trending digest at 1 PM...");

        // 1. Resolve top trending thought headings for the notification body
        List<ThoughtEngagementScore> topScores = scoreRepository.findTop10ByOrderByEngagementScoreDesc();
        List<String> topThoughtIds = topScores.stream()
                .limit(TOP_THOUGHTS_COUNT)
                .map(ThoughtEngagementScore::getThoughtId)
                .collect(Collectors.toList());

        if (topThoughtIds.isEmpty()) {
            log.info("[FeedScheduler] No trending thoughts found, skipping digest.");
            return;
        }

        // Fetch thought headings to compose a human-readable body
        List<String> headings = new ArrayList<>();
        for (String id : topThoughtIds) {
            thoughtRepository.findById(id).map(Thought::getHeading).ifPresent(headings::add);
        }

        String topHeading = headings.isEmpty() ? "See what's trending" : headings.get(0);
        String body = headings.size() == 1
                ? "\"" + topHeading + "\" is trending right now. Check it out!"
                : "\"" + topHeading + "\" and " + (headings.size() - 1) + " more posts are trending today!";

        Map<String, String> data = new HashMap<>();
        data.put("type", "DAILY_TRENDING");
        // Pass the top thought id so the app can deep-link directly if desired
        data.put("postId", topThoughtIds.get(0));
        data.put("deepLink", "app://feed");

        // 2. Fan-out: send to all non-blocked users with an FCM token
        Instant oneDayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        List<User> allUsers = userRepository.findAll();
        int sent = 0, skipped = 0;

        for (User user : allUsers) {
            try {
                if (user.isBlocked()
                        || user.getFcmToken() == null
                        || user.getFcmToken().isBlank()) {
                    skipped++;
                    continue;
                }

                // One digest per user per 24 hours
                if (user.getLastNotificationSentAt() != null
                        && user.getLastNotificationSentAt().isAfter(oneDayAgo)) {
                    skipped++;
                    continue;
                }

                notificationService.createNotification(
                        user.getUserId(),
                        "🔥 Today's trending thoughts",
                        body,
                        NotificationType.FEED,
                        data
                );

                user.setLastNotificationSentAt(Instant.now());
                userRepository.save(user);
                sent++;

            } catch (Exception e) {
                log.error("[FeedScheduler] Failed to send digest to user {}: {}", user.getUserId(), e.getMessage());
                skipped++;
            }
        }

        log.info("[FeedScheduler] Daily trending digest complete. Sent={}, Skipped={}", sent, skipped);
    }
}
