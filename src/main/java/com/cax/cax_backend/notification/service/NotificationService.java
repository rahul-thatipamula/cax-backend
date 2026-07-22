package com.cax.cax_backend.notification.service;

import com.cax.cax_backend.common.enums.NotificationEnums.*;
import com.cax.cax_backend.notification.dto.NotificationAdminView;
import com.cax.cax_backend.notification.model.Notification;
import com.cax.cax_backend.notification.repository.NotificationRepository;
import com.cax.cax_backend.user.model.User;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j @Service @RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;
    private final UserRepository userRepository;
    private final SettingsRepository settingsRepository;
    private final MongoTemplate mongoTemplate;

    public List<Notification> getUserNotifications(String userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Admin-facing browse of every notification ever sent, with optional filters and
     * recipient (user) details attached for display.
     */
    public Page<NotificationAdminView> getAdminNotifications(
            int page, int size, String search, String userId, String collegeId,
            Boolean read, Instant startDate, Instant endDate) {

        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("deleted").ne(true));

        if (search != null && !search.isBlank()) {
            Criteria titleOrBody = new Criteria().orOperator(
                    Criteria.where("title").regex(search, "i"),
                    Criteria.where("body").regex(search, "i")
            );
            criteria.add(titleOrBody);
        }

        if (collegeId != null && !collegeId.isBlank()) {
            List<String> collegeUserIds = userRepository.findByCollegeDetails_CollegeId(collegeId)
                    .stream().map(User::getUserId).toList();
            if (collegeUserIds.isEmpty()) {
                return Page.empty(PageRequest.of(page, size));
            }
            criteria.add(Criteria.where("userId").in(collegeUserIds));
        }

        if (userId != null && !userId.isBlank()) {
            criteria.add(Criteria.where("userId").is(userId));
        }

        if (read != null) {
            criteria.add(Criteria.where("read").is(read));
        }

        if (startDate != null || endDate != null) {
            Criteria dateCriteria = Criteria.where("createdAt");
            if (startDate != null) dateCriteria = dateCriteria.gte(startDate);
            if (endDate != null) dateCriteria = dateCriteria.lte(endDate);
            criteria.add(dateCriteria);
        }

        Query query = new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        long total = mongoTemplate.count(query, Notification.class);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        query.with(pageable);
        List<Notification> notifications = mongoTemplate.find(query, Notification.class);

        List<String> recipientIds = notifications.stream()
                .map(Notification::getUserId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        Map<String, User> usersById = recipientIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findByUserIdIn(recipientIds).stream()
                        .collect(java.util.stream.Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));

        List<NotificationAdminView> views = notifications.stream()
                .map(n -> toAdminView(n, usersById.get(n.getUserId())))
                .toList();

        return new PageImpl<>(views, pageable, total);
    }

    private NotificationAdminView toAdminView(Notification n, User user) {
        NotificationAdminView.Recipient recipient = NotificationAdminView.Recipient.builder()
                .userId(n.getUserId())
                .name(user != null ? user.getName() : null)
                .email(user != null ? user.getEmail() : null)
                .picture(user != null ? user.getPicture() : null)
                .collegeName(user != null && user.getCollegeDetails() != null ? user.getCollegeDetails().getCollegeName() : null)
                .build();

        return NotificationAdminView.builder()
                .id(n.getId())
                .title(n.getTitle())
                .body(n.getBody())
                .type(n.getType())
                .imageUrl(n.getImageUrl())
                .priority(n.getPriority())
                .read(n.isRead())
                .data(n.getData())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .recipient(recipient)
                .build();
    }

    public void markAsRead(String callerId, String notificationId) {
        repo.findById(notificationId).ifPresent(n -> {
            if (n.isDeleted()) {
                return;
            }
            if (!n.getUserId().equals(callerId)) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("You do not own this notification.");
            }
            n.setRead(true);
            n.setReadAt(Instant.now());
            repo.save(n);
        });
    }

    public void deleteNotification(String callerId, String notificationId) {
        repo.findById(notificationId).ifPresent(n -> {
            if (n.isDeleted()) {
                return;
            }
            if (!n.getUserId().equals(callerId)) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("You do not own this notification.");
            }
            n.setDeleted(true);
            n.setDeletedAt(Instant.now());
            repo.save(n);
        });
    }

    public long getUnreadCount(String userId) { return repo.countByUserIdAndReadFalse(userId); }

    public Notification createNotification(String userId, String title, String body, NotificationType type, Map<String, String> data) {
        return createNotification(userId, title, body, type, data, null);
    }

    public Notification createNotification(String userId, String title, String body, NotificationType type, Map<String, String> data, String imageUrl) {
        Map<String, String> enrichedData = new HashMap<>();
        if (data != null) {
            enrichedData.putAll(data);
        }

        // Check if user is blocked or verification has expired
        Optional<com.cax.cax_backend.user.model.User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isPresent()) {
            com.cax.cax_backend.user.model.User user = userOpt.get();
            if (user.isBlocked()) {
                log.debug("User {} is blocked. Skipping notification creation.", userId);
                return null;
            }
            // Inject recipient user engagement score & coins into data map
            enrichedData.putIfAbsent("userCoins", String.valueOf(user.getCoins()));
            enrichedData.putIfAbsent("userTotalCoinsEarned", String.valueOf(user.getTotalCoinsEarned()));
            enrichedData.putIfAbsent("userEngagementScore", String.valueOf(user.getTotalCoinsEarned()));
        }

        boolean notificationsEnabled = true;
        boolean pushNotificationsEnabled = true;

        Optional<UserSettings> settingsOpt = settingsRepository.findAllByUserId(userId).stream().findFirst();
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
                .data(enrichedData)
                .imageUrl(imageUrl)
                .build());

        // Send FCM push notification if user has an FCM token and push notifications are enabled
        if (pushNotificationsEnabled) {
            userRepository.findByUserId(userId).ifPresent(user -> {
                if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
                    Map<String, String> pushData = new HashMap<>(enrichedData);
                    if (imageUrl != null) {
                        pushData.put("imageUrl", imageUrl);
                    }
                    pushData.put("type", (data != null && data.containsKey("type")) ? data.get("type") : type.getValue());
                    sendPushNotification(userId, com.cax.cax_backend.common.util.EncryptionUtils.decrypt(user.getFcmToken()), title, body, pushData);
                } else {
                    log.debug("User {} does not have an FCM token, skipping push notification", userId);
                }
            });
        } else {
            log.debug("Push notifications are disabled for user {}. Skipping FCM push.", userId);
        }

        return notification;
    }

    /**
     * Sends a push notification without persisting it. Use for transient reminders
     * (e.g. water reminders) that should not appear in the in-app notification inbox
     * or count towards the unread badge.
     */
    public void sendTransientPush(String userId, String title, String body, NotificationType type, Map<String, String> data) {
        Optional<User> userOpt = userRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            log.debug("User {} not found. Skipping transient push.", userId);
            return;
        }

        User user = userOpt.get();
        if (user.isBlocked()) {
            log.debug("User {} is blocked. Skipping transient push.", userId);
            return;
        }

        Optional<UserSettings> settingsOpt = settingsRepository.findAllByUserId(userId).stream().findFirst();
        if (settingsOpt.isPresent()) {
            UserSettings settings = settingsOpt.get();
            if (!settings.isNotificationsEnabled() || !settings.isPushNotificationsEnabled()) {
                log.debug("Notifications disabled for user {}. Skipping transient push.", userId);
                return;
            }
        }

        if (user.getFcmToken() == null || user.getFcmToken().isBlank()) {
            log.debug("User {} does not have an FCM token, skipping transient push", userId);
            return;
        }

        Map<String, String> pushData = new HashMap<>();
        if (data != null) {
            pushData.putAll(data);
        }
        pushData.put("type", (data != null && data.containsKey("type")) ? data.get("type") : type.getValue());

        sendPushNotification(
                userId,
                com.cax.cax_backend.common.util.EncryptionUtils.decrypt(user.getFcmToken()),
                title,
                body,
                pushData
        );
    }

    @Async("taskExecutor")
    public void sendNotificationToAll(String title, String body, NotificationType type, Map<String, String> data) {
        sendNotificationToAll(title, body, type, data, null);
    }

    @Async("taskExecutor")
    public void sendNotificationToAll(String title, String body, NotificationType type, Map<String, String> data, String excludeUserId) {
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);
        List<com.cax.cax_backend.user.model.User> users = userRepository.findAll();

        users.forEach(user -> {
            try {
                if (excludeUserId != null && excludeUserId.equals(user.getUserId())) {
                    return;
                }
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
        sendNotificationToCollege(collegeId, title, body, type, data, null);
    }

    /**
     * Fan-out a notification to every student of a single college, skipping {@code excludeUserId}
     * (typically the author, so they aren't disturbed by their own post).
     */
    @Async("taskExecutor")
    public void sendNotificationToCollege(String collegeId, String title, String body, NotificationType type, Map<String, String> data, String excludeUserId) {
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);
        List<com.cax.cax_backend.user.model.User> users = userRepository.findByCollegeDetails_CollegeId(collegeId);

        for (com.cax.cax_backend.user.model.User user : users) {
            if (excludeUserId != null && excludeUserId.equals(user.getUserId())) {
                continue;
            }
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
        log.info("Broadcast Notification to College {} complete. Matching targets: {}, Successes: {}, Failures/Skipped: {}", collegeId, users.size(), successCount.get(), failCount.get());
    }

    @Async("taskExecutor")
    public void sendNotificationToColleges(List<String> collegeIds, String title, String body, NotificationType type, Map<String, String> data) {
        sendNotificationToColleges(collegeIds, title, body, type, data, null);
    }

    /**
     * Fan-out a notification to students across multiple target colleges without duplicate sends.
     */
    @Async("taskExecutor")
    public void sendNotificationToColleges(List<String> collegeIds, String title, String body, NotificationType type, Map<String, String> data, String excludeUserId) {
        if (collegeIds == null || collegeIds.isEmpty()) {
            log.info("No target colleges provided for broadcast notification.");
            return;
        }

        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.Set<com.cax.cax_backend.user.model.User> targetUsers = new java.util.HashSet<>();
        for (String cid : collegeIds) {
            if (cid != null && !cid.isBlank()) {
                targetUsers.addAll(userRepository.findByCollegeDetails_CollegeId(cid));
            }
        }

        for (com.cax.cax_backend.user.model.User user : targetUsers) {
            if (excludeUserId != null && excludeUserId.equals(user.getUserId())) {
                continue;
            }
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
                log.error("Failed to send notification to multi-college user: {}, error: {}", user.getUserId(), e.getMessage());
            }
        }
        log.info("Broadcast Notification to Colleges {} complete. Target count: {}, Successes: {}, Failures/Skipped: {}", collegeIds, targetUsers.size(), successCount.get(), failCount.get());
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

            // Android config for click handling
            AndroidConfig androidConfig = AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(AndroidNotification.builder()
                            .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                            .build())
                    .build();
            messageBuilder.setAndroidConfig(androidConfig);

            // iOS (APNS) config — silent delivery, no sound
            ApnsConfig apnsConfig = ApnsConfig.builder()
                    .setAps(Aps.builder()
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
}
