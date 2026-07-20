package com.cax.cax_backend.arcade.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes out games that everyone walked away from.
 *
 * <p>Sessions advance lazily off client polling, which means an abandoned game simply stops
 * moving rather than ending — it would otherwise sit in its host's "resume" list and hold its
 * join code indefinitely. This sweep is the one piece of the Arcade that needs a clock of its
 * own, and it runs infrequently because nothing is waiting on it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArcadeReaperScheduler {

    private final ArcadeSessionService sessionService;

    /** Every 30 minutes, offset past startup so it does not compete with boot-time work. */
    @Scheduled(initialDelay = 300_000, fixedDelay = 1_800_000)
    public void reap() {
        try {
            int closed = sessionService.reapIdleSessions();
            if (closed > 0) log.info("Arcade: closed {} abandoned session(s)", closed);
        } catch (Exception e) {
            // A failed sweep must never take down the scheduler thread — the next run retries.
            log.warn("Arcade: idle-session sweep failed", e);
        }
    }
}
