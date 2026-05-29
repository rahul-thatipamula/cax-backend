package com.cax.cax_backend.notification.service;

import com.cax.cax_backend.common.enums.NotificationEnums.*;
import com.cax.cax_backend.notification.model.Notification;
import com.cax.cax_backend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j @Service @RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;

    public List<Notification> getUserNotifications(String userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public void markAsRead(String notificationId) {
        repo.findById(notificationId).ifPresent(n -> { n.setRead(true); n.setReadAt(Instant.now()); repo.save(n); });
    }

    public void deleteNotification(String notificationId) { repo.deleteById(notificationId); }

    public long getUnreadCount(String userId) { return repo.countByUserIdAndReadFalse(userId); }

    public Notification createNotification(String userId, String title, String body, NotificationType type, Map<String, String> data) {
        return repo.save(Notification.builder().userId(userId).title(title).body(body).type(type).data(data).build());
    }
}
