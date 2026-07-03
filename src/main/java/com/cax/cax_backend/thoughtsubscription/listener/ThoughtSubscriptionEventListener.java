package com.cax.cax_backend.thoughtsubscription.listener;

import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.service.NotificationService;
import com.cax.cax_backend.thoughtsubscription.event.ThoughtSubscribedEvent;
import com.cax.cax_backend.thoughtsubscription.model.ThoughtSubscription;
import com.cax.cax_backend.thoughtsubscription.service.ThoughtSubscriptionService;
import com.cax.cax_backend.thought.event.ThoughtCreatedEvent;
import com.cax.cax_backend.thought.model.Thought;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThoughtSubscriptionEventListener {

    private final ThoughtSubscriptionService subscriptionService;
    private final NotificationService notificationService;

    @Async("taskExecutor")
    @EventListener
    public void handleThoughtCreated(ThoughtCreatedEvent event) {
        Thought thought = event.getThought();
        List<ThoughtSubscription> subscribers = subscriptionService.listSubscribersOf(thought.getUserId());
        if (subscribers.isEmpty()) return;

        for (ThoughtSubscription subscription : subscribers) {
            try {
                Map<String, String> data = new HashMap<>();
                data.put("type", "THOUGHT_SUBSCRIPTION");
                data.put("postId", thought.getId());
                data.put("authorId", thought.getUserId());
                data.put("authorName", thought.getCreatorName());
                data.put("deepLink", "app://feed/post/" + thought.getId());

                notificationService.createNotification(
                        subscription.getSubscriberId(),
                        "New thought from " + thought.getCreatorName(),
                        thought.getHeading(),
                        NotificationType.FEED,
                        data,
                        thought.getCreatorPicture()
                );
            } catch (Exception e) {
                log.error("Failed to notify subscriber {} of new thought {}: {}",
                        subscription.getSubscriberId(), thought.getId(), e.getMessage());
            }
        }
    }

    @Async("taskExecutor")
    @EventListener
    public void handleThoughtSubscribed(ThoughtSubscribedEvent event) {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "NEW_SUBSCRIBER");
            data.put("subscriberId", event.getSubscriberId());
            data.put("subscriberName", event.getSubscriberName());
            data.put("deepLink", "app://profile/" + event.getSubscriberId());

            notificationService.createNotification(
                    event.getAuthorId(),
                    "New Subscriber",
                    event.getSubscriberName() + " subscribed to your thoughts",
                    NotificationType.FEED,
                    data,
                    event.getSubscriberPicture()
            );
        } catch (Exception e) {
            log.error("Failed to notify author {} of new subscriber {}: {}",
                    event.getAuthorId(), event.getSubscriberId(), e.getMessage());
        }
    }
}
