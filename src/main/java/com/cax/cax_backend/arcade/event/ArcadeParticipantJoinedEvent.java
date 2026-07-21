package com.cax.cax_backend.arcade.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Raised when a genuinely new player joins a lobby (not a rejoin, and not the host joining their
 * own game). Consumed asynchronously so the join request itself never waits on a push send.
 */
@Getter
@RequiredArgsConstructor
public class ArcadeParticipantJoinedEvent {

    private final String gameCode;
    /** The host, who receives the "someone joined" notification. */
    private final String hostUserId;
    private final String joinedCaxId;
    private final String joinedDisplayName;
}
