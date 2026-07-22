package com.cax.cax_backend.bulletinevent.listener;

import com.cax.cax_backend.bulletinevent.event.BulletinEventCreatedEvent;
import com.cax.cax_backend.bulletinevent.model.BulletinEvent;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BulletinEventCreatedListener {

    private final NotificationService notificationService;

    @Async("taskExecutor")
    @EventListener
    public void handleBulletinEventCreatedEvent(BulletinEventCreatedEvent event) {
        BulletinEvent bulletinEvent = event.getBulletinEvent();
        if (bulletinEvent == null) {
            return;
        }

        log.info("Processing BulletinEventCreatedEvent for event: '{}' (id={}), global={}",
                bulletinEvent.getTitle(), bulletinEvent.getId(), bulletinEvent.isGlobal());

        try {
            String title = bulletinEvent.getTitle();
            String body = bulletinEvent.getDescription();
            if (body != null && body.length() > 200) {
                body = body.substring(0, 197) + "...";
            }

            Map<String, String> data = new HashMap<>();
            data.put("type", "BULLETIN_EVENT");
            data.put("eventId", bulletinEvent.getId());
            if (bulletinEvent.getTitle() != null) {
                data.put("title", bulletinEvent.getTitle());
            }
            if (bulletinEvent.getDescription() != null) {
                data.put("description", bulletinEvent.getDescription());
            }
            if (bulletinEvent.getEventStartDate() != null) {
                data.put("eventStartDate", bulletinEvent.getEventStartDate().toString());
            }
            if (bulletinEvent.getEventEndDate() != null) {
                data.put("eventEndDate", bulletinEvent.getEventEndDate().toString());
            }
            if (bulletinEvent.getRegistrationEndDate() != null) {
                data.put("registrationEndDate", bulletinEvent.getRegistrationEndDate().toString());
                data.put("deadline", bulletinEvent.getRegistrationEndDate().toString());
            }
            if (bulletinEvent.getExternalLink() != null) {
                data.put("externalLink", bulletinEvent.getExternalLink());
            }
            if (bulletinEvent.getConductedBy() != null) {
                data.put("conductedBy", bulletinEvent.getConductedBy());
            }
            if (bulletinEvent.getCoverImage() != null) {
                data.put("coverImage", bulletinEvent.getCoverImage());
            }
            data.put("isGlobal", String.valueOf(bulletinEvent.isGlobal()));

            String imageUrl = bulletinEvent.getCoverImage();

            if (bulletinEvent.isGlobal()) {
                log.info("Sending global event notification to all users for bulletin event: {}", bulletinEvent.getId());
                notificationService.sendNotificationToAll(title, body, NotificationType.EVENT, data);
            } else if (bulletinEvent.getCollegeIds() != null && !bulletinEvent.getCollegeIds().isEmpty()) {
                log.info("Sending college-scoped event notification to colleges {} for bulletin event: {}",
                        bulletinEvent.getCollegeIds(), bulletinEvent.getId());
                notificationService.sendNotificationToColleges(bulletinEvent.getCollegeIds(), title, body, NotificationType.EVENT, data);
            } else {
                log.warn("Bulletin event {} is not global and has no target collegeIds. Skipping notification dispatch.", bulletinEvent.getId());
            }
        } catch (Exception e) {
            log.error("Failed to process BulletinEventCreatedEvent for event: {}", bulletinEvent.getId(), e);
        }
    }
}
