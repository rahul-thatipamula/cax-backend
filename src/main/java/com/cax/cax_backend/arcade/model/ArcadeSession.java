package com.cax.cax_backend.arcade.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One play-through of one Arcade game, identified by a short join code.
 *
 * <p>This document holds only the shared session machinery. Per-round content lives in
 * {@link ArcadeRound}, per-player state in {@link ArcadeParticipant}, and the final
 * standings snapshot in {@link ArcadeResult} — separate collections linked by id, so a
 * session document stays small and no query has to load a whole game to read one part of it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "arcade_sessions")
public class ArcadeSession {

    @Id
    private String id;

    /** Short human-typeable join code. Unique across all games and all types. */
    @Indexed(unique = true)
    private String gameCode;

    private ArcadeGameType gameType;

    /** Internal userId of the host — the only player allowed to start/advance/end the session. */
    @Indexed
    private String hostUserId;

    private String hostCaxId;

    /** Denormalised so history lists render without joining the user collection. */
    private String hostName;

    /** College the host belongs to, kept for history filtering and abuse tracing. */
    private String collegeId;

    @Builder.Default
    private ArcadePhase phase = ArcadePhase.LOBBY;

    /**
     * Monotonic counter bumped on every mutation. Clients poll with the last version they
     * rendered and the server returns an empty body when nothing has changed, so an idle
     * lobby of 30 phones costs one cheap indexed read each rather than a full state build.
     */
    @Builder.Default
    private long stateVersion = 1L;

    /** 0 while in LOBBY; 1-based once play starts. */
    @Builder.Default
    private int currentRound = 0;

    /** Total rounds to play. Bounded at creation time. */
    @Builder.Default
    private int totalRounds = 5;

    /**
     * When the current phase auto-advances. Every timed phase has one so a player who
     * closes the app mid-round can never stall the room indefinitely.
     */
    private Instant phaseDeadlineAt;

    /** Optional score-based end condition; when non-null, first player to reach it ends the game. */
    private Integer targetScore;

    private int submitSeconds;
    private int voteSeconds;
    private int revealSeconds;
    private int intermissionSeconds;

    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Bumped on every host action and every player poll — drives idle-session reaping. */
    @Builder.Default
    private Instant lastActivityAt = Instant.now();

    private Instant startedAt;
    private Instant endedAt;

    /** Set when the session ended for a reason other than playing out all rounds. */
    private String endReason;

    /**
     * Prompt ids already used in this session, so a five-round game never asks the same
     * question twice. Bounded by totalRounds, so it stays tiny.
     */
    @Builder.Default
    private java.util.List<String> usedPromptIds = new java.util.ArrayList<>();

    public boolean isFinished() {
        return phase == ArcadePhase.FINISHED;
    }

    public boolean isInLobby() {
        return phase == ArcadePhase.LOBBY;
    }
}
