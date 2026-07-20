package com.cax.cax_backend.notification.scheduler;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
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

@Component
@Slf4j
@RequiredArgsConstructor
public class WaterReminderScheduler {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // Run every 15 minutes to check for eligible users
    @Scheduled(cron = "0 */15 * * * *")
    public void sendWaterReminders() {
        // Get current time in Indian Standard Time (Asia/Kolkata)
        java.time.ZonedDateTime nowIst = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        int hour = nowIst.getHour();

        // Only send reminders from morning 6:00 AM to evening 9:00 PM (inclusive)
        if (hour >= 6 && hour <= 21) {
            log.info("Starting scheduled water reminder run for hour {}...", hour);

            // Water reminders are sent every 1 hr 15 mins (75 minutes)
            Instant threshold = Instant.now().minus(75, ChronoUnit.MINUTES);
            List<User> eligibleUsers = userRepository.findUsersForWaterReminder(threshold);

            if (!eligibleUsers.isEmpty()) {
                log.info("Found {} users eligible for water reminder.", eligibleUsers.size());
                for (User user : eligibleUsers) {
                    try {
                        // Push-only: hydration reminders are transient and must not be
                        // persisted to the notification inbox / unread count.
                        notificationService.sendTransientPush(
                                user.getUserId(),
                                "Stay Hydrated",
                                "This is a reminder to drink a glass of water to maintain hydration.",
                                NotificationType.SYSTEM,
                                new HashMap<>()
                        );

                        // Update the last sent timestamp
                        user.setLastWaterReminderSentAt(Instant.now());
                        userRepository.save(user);
                    } catch (Exception e) {
                        log.error("Failed to send water reminder to user {}: {}", user.getUserId(), e.getMessage());
                    }
                }
            }
        }
    }
}
