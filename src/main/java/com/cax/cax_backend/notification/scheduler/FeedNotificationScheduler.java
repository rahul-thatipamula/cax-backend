package com.cax.cax_backend.notification.scheduler;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.studentpost.repository.StudentPostRepository;
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

@Component
@Slf4j
@RequiredArgsConstructor
public class FeedNotificationScheduler {

    private final UserRepository userRepository;
    private final StudentPostRepository studentPostRepository;
    private final NotificationService notificationService;

    // Runs once a day at 7:30 PM (19:30) server time
    @Scheduled(cron = "0 30 19 * * *")
    public void sendUnseenFeedNotifications() {
        log.info("Starting scheduled unseen feed notification run...");
        
        // Target users active in the last 7 days
        Instant activeThreshold = Instant.now().minus(7, ChronoUnit.DAYS);
        List<User> users = userRepository.findActiveNotificationEligibleUsers(activeThreshold);
        Instant oneDayAgo = Instant.now().minus(24, ChronoUnit.HOURS);

        for (User user : users) {
            // Skip blocked or token-less users
            if (user.isBlocked() || user.getFcmToken() == null || user.getFcmToken().isBlank()) {
                continue;
            }

            // Frequency Cap: Don't send more than once every 24 hours
            if (user.getLastNotificationSentAt() != null && user.getLastNotificationSentAt().isAfter(oneDayAgo)) {
                continue;
            }

            // Default lastSeenFeedAt to 24 hours ago if they have never opened the feed
            Instant lastSeen = user.getLastSeenFeedAt() != null ? user.getLastSeenFeedAt() : oneDayAgo;

            long unseenCount = studentPostRepository.countActiveExcludingUserSince(user.getUserId(), lastSeen);

            if (unseenCount > 0) {
                try {
                    Map<String, String> data = new HashMap<>();
                    data.put("type", "FEED_DIGEST");
                    data.put("unseenCount", String.valueOf(unseenCount));
                    
                    String title = "🔥 What's happening on CAX?";
                    String body = unseenCount == 1 
                            ? "There is 1 new thought in the feed. Check it out!"
                            : String.format("There are %d new thoughts in the feed. Check them out!", unseenCount);

                    log.info("Sending feed nudge to user {} with {} unseen thoughts", user.getUserId(), unseenCount);
                    
                    // Creates db notification entry and triggers FCM push via NotificationService
                    notificationService.createNotification(
                            user.getUserId(),
                            title,
                            body,
                            NotificationType.FEED,
                            data
                    );

                    // Update last notification timestamp
                    user.setLastNotificationSentAt(Instant.now());
                    userRepository.save(user);

                } catch (Exception e) {
                    log.error("Failed to send feed nudge to user: {}", user.getUserId(), e);
                }
            }
        }
        log.info("Scheduled unseen feed notification run complete.");
    }
}
