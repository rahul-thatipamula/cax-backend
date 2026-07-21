package com.cax.cax_backend.arcade.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends nudges to a game's subscribers over STOMP. A failed send must never break the game
 * logic that triggered it — the client's fallback poll still carries the change — so every
 * publish is best-effort and swallows its own errors.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArcadeBroadcaster {

    private final SimpMessagingTemplate messaging;

    public void stateChanged(String gameCode) {
        send(ArcadeBroadcast.state(gameCode));
    }

    public void playerJoined(String gameCode, String joinedName) {
        send(ArcadeBroadcast.joined(gameCode, joinedName));
    }

    private void send(ArcadeBroadcast msg) {
        try {
            messaging.convertAndSend("/topic/arcade/" + msg.getGameCode(), msg);
        } catch (Exception e) {
            log.debug("Arcade broadcast failed for {}: {}", msg.getGameCode(), e.getMessage());
        }
    }
}
