package com.cax.cax_backend.studentpost.listener;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.studentpost.event.StudentPostCommentedEvent;
import com.cax.cax_backend.studentpost.event.StudentPostLikedEvent;
import com.cax.cax_backend.studentpost.event.StudentPostDisabledEvent;
import com.cax.cax_backend.studentpost.model.StudentPost;
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
public class StudentPostEventListener {

    private final NotificationService notificationService;
    private final UserService userService;

    @Async("taskExecutor")
    @EventListener
    public void handleStudentPostLikedEvent(StudentPostLikedEvent event) {
        StudentPost post = event.getPost();
        String actorId = event.getLikedByUserId();

        // Don't notify the post owner if they liked their own post
        if (post.getUserId().equals(actorId)) {
            return;
        }

        try {
            User actor = userService.getUserByUserId(actorId);
            String title = "New Like";
            String body = actor.getName() + " liked your thought: \"" + post.getHeading() + "\"";

            Map<String, String> data = new HashMap<>();
            data.put("type", "POST_LIKE");
            data.put("postId", post.getId());
            data.put("actorId", actorId);
            data.put("actorName", actor.getName());
            data.put("deepLink", "app://feed/post/" + post.getId());

            // Set post image thumbnail if available
            if (post.getImages() != null && !post.getImages().isEmpty()) {
                data.put("postImage", post.getImages().get(0).getUrl());
            }

            notificationService.createNotification(
                    post.getUserId(),
                    title,
                    body,
                    NotificationType.FEED,
                    data,
                    actor.getPicture() // Actor's picture as notification avatar
            );
            log.info("Sent thought liked notification to user: {} from: {}", post.getUserId(), actorId);
        } catch (Exception e) {
            log.error("Failed to process StudentPostLikedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @EventListener
    public void handleStudentPostCommentedEvent(StudentPostCommentedEvent event) {
        StudentPost post = event.getPost();
        StudentPost.Comment comment = event.getComment();
        String actorId = comment.getUserId();

        // Don't notify the post owner if they commented on their own post
        if (post.getUserId().equals(actorId)) {
            return;
        }

        try {
            User actor = userService.getUserByUserId(actorId);
            String title = "New Comment";
            String body = actor.getName() + " commented: \"" + comment.getText() + "\"";

            Map<String, String> data = new HashMap<>();
            data.put("type", "POST_COMMENT");
            data.put("postId", post.getId());
            data.put("commentId", comment.getId());
            data.put("actorId", actorId);
            data.put("actorName", actor.getName());
            data.put("deepLink", "app://feed/post/" + post.getId());

            // Set post image thumbnail if available
            if (post.getImages() != null && !post.getImages().isEmpty()) {
                data.put("postImage", post.getImages().get(0).getUrl());
            }

            notificationService.createNotification(
                    post.getUserId(),
                    title,
                    body,
                    NotificationType.FEED,
                    data,
                    actor.getPicture() // Actor's picture as notification avatar
            );
            log.info("Sent thought commented notification to user: {} from: {}", post.getUserId(), actorId);
        } catch (Exception e) {
            log.error("Failed to process StudentPostCommentedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @EventListener
    public void handleStudentPostDisabledEvent(StudentPostDisabledEvent event) {
        StudentPost post = event.getPost();
        try {
            String title = "Thought Moderated";
            String body = "Your thought \"" + post.getHeading() + "\" has been disabled for violating community guidelines.";

            Map<String, String> data = new HashMap<>();
            data.put("type", "POST_DISABLED");
            data.put("postId", post.getId());
            data.put("deepLink", "app://feed/post/" + post.getId());

            // Image thumbnail if available
            if (post.getImages() != null && !post.getImages().isEmpty()) {
                data.put("postImage", post.getImages().get(0).getUrl());
            }

            notificationService.createNotification(
                    post.getUserId(),
                    title,
                    body,
                    NotificationType.FEED,
                    data
            );
            log.info("Sent thought disabled notification to user: {}", post.getUserId());
        } catch (Exception e) {
            log.error("Failed to process StudentPostDisabledEvent: {}", e.getMessage(), e);
        }
    }
}
