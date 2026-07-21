package com.cax.cax_backend.organization.listener;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.organization.event.OrganizationPostCreatedEvent;
import com.cax.cax_backend.organization.model.OrganizationPost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Notifies every student of an organization's college whenever that organization
 * publishes a new post. Targeting is entirely server-side: the college id is read
 * from the persisted {@link OrganizationPost} (copied from the {@code Organization}
 * at creation time, never from client input), and the fan-out itself
 * ({@link NotificationService#sendNotificationToCollege}) re-checks each recipient's
 * college id, block status and notification settings before sending — so a student
 * can only ever be notified about posts from organizations at their own college.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationPostNotificationListener {

    private static final int CAPTION_PREVIEW_LENGTH = 120;

    private final NotificationService notificationService;

    @Async("taskExecutor")
    @EventListener
    public void handleOrganizationPostCreated(OrganizationPostCreatedEvent event) {
        OrganizationPost post = event.getPost();

        if (post.getCollegeId() == null || post.getCollegeId().isBlank()) {
            log.warn("Organization post {} has no collegeId; skipping notification fan-out.", post.getId());
            return;
        }

        String organizationName = post.getOrganizationName() != null ? post.getOrganizationName() : "An organization";

        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "ORG_POST_CREATED");
            data.put("postId", post.getId());
            data.put("organizationId", post.getOrganizationId());
            data.put("organizationName", organizationName);
            data.put("deepLink", "app://club/post/" + post.getId());

            notificationService.sendNotificationToCollege(
                    post.getCollegeId(),
                    organizationName,
                    buildBody(post),
                    NotificationType.FEED,
                    data,
                    post.getCreatorId()
            );
        } catch (Exception e) {
            log.error("Failed to notify college {} of new post {} from organization {}: {}",
                    post.getCollegeId(), post.getId(), post.getOrganizationId(), e.getMessage());
        }
    }

    private String buildBody(OrganizationPost post) {
        if (post.isPoll() && post.getPollQuestion() != null && !post.getPollQuestion().isBlank()) {
            return "New poll: " + truncate(post.getPollQuestion());
        }
        if (post.getCaption() != null && !post.getCaption().isBlank()) {
            return truncate(post.getCaption());
        }
        return "Shared a new update. Tap to view.";
    }

    private String truncate(String text) {
        String trimmed = text.trim();
        if (trimmed.length() <= CAPTION_PREVIEW_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, CAPTION_PREVIEW_LENGTH).trim() + "…";
    }
}
