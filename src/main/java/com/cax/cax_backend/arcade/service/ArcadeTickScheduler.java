package com.cax.cax_backend.arcade.service;

import com.cax.cax_backend.arcade.ws.ArcadeBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drives phase deadlines on a server-side clock.
 *
 * <p>The games advance lazily off client activity for the "everyone answered early" case, but a
 * timer running out needs a heartbeat of its own now that clients lean on the WebSocket instead
 * of polling every second — otherwise a room that all goes quiet would freeze on the clock. Each
 * tick advances any session whose deadline has passed and nudges its subscribers to refetch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArcadeTickScheduler {

    private final ArcadeSessionService sessionService;
    private final ArcadeBroadcaster broadcaster;

    @Scheduled(fixedDelay = 1_000)
    public void tick() {
        try {
            List<String> changed = sessionService.advanceDueSessions();
            for (String gameCode : changed) {
                broadcaster.stateChanged(gameCode);
            }
        } catch (Exception e) {
            // Never let a bad tick kill the scheduler thread; the next tick retries.
            log.warn("Arcade: deadline tick failed", e);
        }
    }
}
