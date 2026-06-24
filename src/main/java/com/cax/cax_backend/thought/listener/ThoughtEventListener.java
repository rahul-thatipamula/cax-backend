package com.cax.cax_backend.thought.listener;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.thought.event.ThoughtCommentedEvent;
import com.cax.cax_backend.thought.event.ThoughtDisabledEvent;
import com.cax.cax_backend.thought.event.ThoughtLikedEvent;
import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.service.ThoughtEngagementService;
import com.cax.cax_backend.user.model.User;
import com.cax.cax_backend.user.service.UserService;
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
public class ThoughtEventListener {

    private final NotificationService notificationService;
    private final UserService userService;
    private final ThoughtEngagementService engagementService;

    @Async("taskExecutor")
    @EventListener
    public void handleThoughtLiked(ThoughtLikedEvent event) {
        Thought thought = event.getThought();
        String actorId = event.getLikedByUserId();

        // Update engagement score
        engagementService.onLikeChanged(thought);

        if (thought.getUserId().equals(actorId)) return;

        try {
            User actor = userService.getUserByUserId(actorId);
            Map<String, String> data = new HashMap<>();
            data.put("type", "POST_LIKE");
            data.put("postId", thought.getId());
            data.put("actorId", actorId);
            data.put("actorName", actor.getName());
            data.put("deepLink", "app://feed/post/" + thought.getId());
            if (thought.getImages() != null && !thought.getImages().isEmpty()) {
                data.put("postImage", thought.getImages().get(0).getUrl());
            }
            notificationService.createNotification(
                    thought.getUserId(),
                    "New Like",
                    actor.getName() + " liked your thought: \"" + thought.getHeading() + "\"",
                    NotificationType.FEED,
                    data,
                    actor.getPicture()
            );
        } catch (Exception e) {
            log.error("Failed to process ThoughtLikedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @EventListener
    public void handleThoughtCommented(ThoughtCommentedEvent event) {
        Thought thought = event.getThought();
        Thought.Comment comment = event.getComment();
        String actorId = comment.getUserId();

        // Update engagement score
        engagementService.onCommentChanged(thought);

        if (thought.getUserId().equals(actorId)) return;

        try {
            User actor = userService.getUserByUserId(actorId);
            Map<String, String> data = new HashMap<>();
            data.put("type", "POST_COMMENT");
            data.put("postId", thought.getId());
            data.put("commentId", comment.getId());
            data.put("actorId", actorId);
            data.put("actorName", actor.getName());
            data.put("deepLink", "app://feed/post/" + thought.getId());
            if (thought.getImages() != null && !thought.getImages().isEmpty()) {
                data.put("postImage", thought.getImages().get(0).getUrl());
            }
            notificationService.createNotification(
                    thought.getUserId(),
                    "New Comment",
                    actor.getName() + " commented: \"" + comment.getText() + "\"",
                    NotificationType.FEED,
                    data,
                    actor.getPicture()
            );
        } catch (Exception e) {
            log.error("Failed to process ThoughtCommentedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @EventListener
    public void handleThoughtDisabled(ThoughtDisabledEvent event) {
        Thought thought = event.getThought();
        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "POST_DISABLED");
            data.put("postId", thought.getId());
            data.put("deepLink", "app://feed/post/" + thought.getId());
            if (thought.getImages() != null && !thought.getImages().isEmpty()) {
                data.put("postImage", thought.getImages().get(0).getUrl());
            }
            notificationService.createNotification(
                    thought.getUserId(),
                    "Thought Moderated",
                    "Your thought \"" + thought.getHeading() + "\" has been disabled for violating community guidelines.",
                    NotificationType.FEED,
                    data
            );
        } catch (Exception e) {
            log.error("Failed to process ThoughtDisabledEvent: {}", e.getMessage(), e);
        }
    }
}
