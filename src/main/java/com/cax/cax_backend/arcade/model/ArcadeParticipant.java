package com.cax.cax_backend.arcade.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One player's membership in one session. This is the durable identity for a player:
 * it is created once on first join and then reused forever.
 *
 * <p>Because score, per-round bookkeeping and ready-state all live here rather than in
 * client memory, a player who backgrounds the app, loses signal or force-quits and rejoins
 * lands back on the same document and continues with the score they already had. There is
 * no "rejoin as a new player" path — {@code (sessionId, caxId)} is uniquely indexed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "arcade_participants")
@CompoundIndex(name = "session_player_unique", def = "{'sessionId': 1, 'caxId': 1}", unique = true)
@CompoundIndex(name = "session_score", def = "{'sessionId': 1, 'score': -1}")
public class ArcadeParticipant {

    @Id
    private String id;

    /** Reference to {@link ArcadeSession#getId()}. */
    @Indexed
    private String sessionId;

    /** Denormalised for history queries that filter by code without loading the session. */
    private String gameCode;

    @Indexed
    private String caxId;

    /** Internal userId — used for host checks, which are keyed on userId not caxId. */
    private String userId;

    /** Snapshot of the display name at join time; refreshed on each rejoin. */
    private String displayName;

    private String avatarUrl;

    @Builder.Default
    private boolean host = false;

    /** Server-authoritative running score. Never accepted from a client. */
    @Builder.Default
    private int score = 0;

    /** Rounds this player actually participated in — used for the per-user history stats. */
    @Builder.Default
    private int roundsPlayed = 0;

    /**
     * Last time this player polled or acted. Presence ("who's here / who's left") is derived
     * from this rather than from a socket, so it survives app backgrounding and network flaps.
     */
    @Builder.Default
    private Instant lastSeenAt = Instant.now();

    /**
     * The round number this player has confirmed they are ready for. The session only advances
     * out of INTERMISSION once every currently-present player has acknowledged the next round.
     */
    @Builder.Default
    private int readyForRound = 0;

    /** Set when the player explicitly leaves; cleared on rejoin. Their score is never cleared. */
    private Instant leftAt;

    /** How many times this player has come back after leaving or timing out. */
    @Builder.Default
    private int rejoinCount = 0;

    @Builder.Default
    private Instant joinedAt = Instant.now();

    /**
     * Presence window. A player is considered present if they have polled recently; anything
     * staler counts as away, which is what lets the round gate skip absent players.
     */
    public boolean isPresent(Instant now, long presenceWindowSeconds) {
        if (leftAt != null) return false;
        return lastSeenAt != null && lastSeenAt.isAfter(now.minusSeconds(presenceWindowSeconds));
    }
}
