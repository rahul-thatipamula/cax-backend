package com.cax.cax_backend.notification.service;

import com.cax.cax_backend.common.enums.NotificationEnums.*;
import com.cax.cax_backend.notification.model.Notification;
import com.cax.cax_backend.notification.repository.NotificationRepository;
import com.cax.cax_backend.user.repository.UserRepository;
import com.cax.cax_backend.settings.model.UserSettings;
import com.cax.cax_backend.settings.repository.SettingsRepository;
import java.util.Optional;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j @Service @RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;
    private final UserRepository userRepository;
    private final SettingsRepository settingsRepository;

    public List<Notification> getUserNotifications(String userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public void markAsRead(String notificationId) {
        repo.findById(notificationId).ifPresent(n -> { n.setRead(true); n.setReadAt(Instant.now()); repo.save(n); });
    }

    public void deleteNotification(String notificationId) { repo.deleteById(notificationId); }

    public long getUnreadCount(String userId) { return repo.countByUserIdAndReadFalse(userId); }

    public Notification createNotification(String userId, String title, String body, NotificationType type, Map<String, String> data) {
        return createNotification(userId, title, body, type, data, null);
    }

    public Notification createNotification(String userId, String title, String body, NotificationType type, Map<String, String> data, String imageUrl) {
        // Check if user is blocked or verification has expired
        Optional<com.cax.cax_backend.user.model.User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isPresent()) {
            com.cax.cax_backend.user.model.User user = userOpt.get();
            if (user.isBlocked()) {
                log.debug("User {} is blocked. Skipping notification creation.", userId);
                return null;
            }
        }

        boolean notificationsEnabled = true;
        boolean pushNotificationsEnabled = true;

        Optional<UserSettings> settingsOpt = settingsRepository.findByUserId(userId);
        if (settingsOpt.isPresent()) {
            UserSettings settings = settingsOpt.get();
            notificationsEnabled = settings.isNotificationsEnabled();
            pushNotificationsEnabled = settings.isPushNotificationsEnabled();
        }

        if (!notificationsEnabled) {
            log.debug("Notifications are globally disabled for user {}. Skipping creation.", userId);
            return null;
        }

        Notification notification = repo.save(Notification.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .type(type)
                .data(data)
                .imageUrl(imageUrl)
                .build());

        // Send FCM push notification if user has an FCM token and push notifications are enabled
        if (pushNotificationsEnabled) {
            userRepository.findByUserId(userId).ifPresent(user -> {
                if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
                    Map<String, String> pushData = new HashMap<>();
                    if (data != null) {
                        pushData.putAll(data);
                    }
                    if (imageUrl != null) {
                        pushData.put("imageUrl", imageUrl);
                    }
                    pushData.put("type", (data != null && data.containsKey("type")) ? data.get("type") : type.getValue());
                    sendPushNotification(userId, user.getFcmToken(), title, body, pushData);
                } else {
                    log.debug("User {} does not have an FCM token, skipping push notification", userId);
                }
            });
        } else {
            log.debug("Push notifications are disabled for user {}. Skipping FCM push.", userId);
        }

        return notification;
    }

    @Async("taskExecutor")
    public void sendNotificationToAll(String title, String body, NotificationType type, Map<String, String> data) {
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);
        List<com.cax.cax_backend.user.model.User> users = userRepository.findAll();

        users.forEach(user -> {
            try {
                if (!user.isBlocked()) {
                    Notification notif = createNotification(user.getUserId(), title, body, type, data);
                    if (notif != null) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                failCount.incrementAndGet();
                log.error("Failed to send notification to user: {}, error: {}", user.getUserId(), e.getMessage());
            }
        });
        log.info("Broadcast Notification to ALL complete. Total targets: {}, Successes: {}, Failures/Skipped: {}", users.size(), successCount.get(), failCount.get());
    }

    @Async("taskExecutor")
    public void sendNotificationToCollege(String collegeId, String title, String body, NotificationType type, Map<String, String> data) {
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);
        List<com.cax.cax_backend.user.model.User> users = userRepository.findAll();
        int matchingTargets = 0;

        for (com.cax.cax_backend.user.model.User user : users) {
            if (user.getCollegeDetails() != null && collegeId.equals(user.getCollegeDetails().getCollegeId())) {
                matchingTargets++;
                try {
                    if (!user.isBlocked()) {
                        Notification notif = createNotification(user.getUserId(), title, body, type, data);
                        if (notif != null) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("Failed to send notification to college user: {}, error: {}", user.getUserId(), e.getMessage());
                }
            }
        }
        log.info("Broadcast Notification to College {} complete. Matching targets: {}, Successes: {}, Failures/Skipped: {}", collegeId, matchingTargets, successCount.get(), failCount.get());
    }

    public void sendPushNotification(String userId, String fcmToken, String title, String body, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("FCM token is empty, skipping push notification");
            return;
        }

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                log.warn("Firebase App is not initialized. Skipping push notification.");
                return;
            }

            com.google.firebase.messaging.Notification notification = com.google.firebase.messaging.Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            Message.Builder messageBuilder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(notification);

            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            // Android config for push alert sound & clicks
            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder()
                            .setSound("cax_alert")
                            .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                            .build())
                    .build();
            messageBuilder.setAndroidConfig(androidConfig);

            // iOS (APNS) config for push alert sound
            ApnsConfig apnsConfig = ApnsConfig.builder()
                    .setAps(Aps.builder()
                            .setSound("default")
                            .setContentAvailable(true)
                            .build())
                    .build();
            messageBuilder.setApnsConfig(apnsConfig);

            Message message = messageBuilder.build();
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent FCM message: {}", response);
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                log.info("FCM token for user {} is unregistered/invalid. Clearing token from database.", userId);
                if (userId != null) {
                    userRepository.findByUserId(userId).ifPresent(u -> {
                        u.setFcmToken(null);
                        userRepository.save(u);
                    });
                }
            } else {
                log.error("Failed to send FCM message due to messaging error: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to send FCM message: {}", e.getMessage(), e);
        }
    }

    public void markClubChatAsRead(String userId, String clubId) {
        List<Notification> unread = repo.findUnreadChatNotifications(userId, NotificationType.CLUB_CHAT, clubId);
        if (unread != null && !unread.isEmpty()) {
            Instant now = Instant.now();
            for (Notification n : unread) {
                n.setRead(true);
                n.setReadAt(now);
            }
            repo.saveAll(unread);
            log.info("Marked {} chat notifications as read for user {} in club {}", unread.size(), userId, clubId);
        }
    }
}
