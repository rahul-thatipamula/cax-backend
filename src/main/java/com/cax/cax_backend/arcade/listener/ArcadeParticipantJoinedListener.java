package com.cax.cax_backend.arcade.listener;

import com.cax.cax_backend.arcade.event.ArcadeParticipantJoinedEvent;
import com.cax.cax_backend.arcade.ws.ArcadeBroadcaster;
import com.cax.cax_backend.common.enums.NotificationEnums.NotificationType;
import com.cax.cax_backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns a lobby join into two notifications, off the request thread: a push to the host (who may
 * not be looking at the screen) and a live nudge to everyone already in the lobby so the "who's
 * here" list updates without waiting for the next fallback poll. A failure in either must not
 * surface back into the join call, so the whole thing is wrapped and logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArcadeParticipantJoinedListener {

    private final NotificationService notificationService;
    private final ArcadeBroadcaster broadcaster;

    @Async("taskExecutor")
    @EventListener
    public void onJoined(ArcadeParticipantJoinedEvent event) {
        // Live lobby feed for players already subscribed to the game topic.
        broadcaster.playerJoined(event.getGameCode(), event.getJoinedDisplayName());

        try {
            Map<String, String> data = new HashMap<>();
            data.put("type", "ARCADE_JOIN");
            data.put("gameCode", event.getGameCode());
            data.put("joinedCaxId", event.getJoinedCaxId());
            data.put("deepLink", "app://arcade/" + event.getGameCode());
            notificationService.createNotification(
                    event.getHostUserId(),
                    "New Player",
                    event.getJoinedDisplayName() + " joined your game",
                    NotificationType.ARCADE,
                    data);
        } catch (Exception e) {
            log.error("Failed to notify host of arcade join for {}: {}", event.getGameCode(), e.getMessage(), e);
        }
    }
}
