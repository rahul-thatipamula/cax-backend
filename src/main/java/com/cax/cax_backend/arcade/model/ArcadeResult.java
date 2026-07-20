package com.cax.cax_backend.arcade.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An immutable snapshot of a finished session's final standings, written once when the
 * session ends.
 *
 * <p>History exists as its own collection rather than being recomputed from participants
 * on every read: the standings are frozen at end time, so the history screen is a single
 * indexed lookup and stays correct even if a display name changes or old rounds are reaped.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "arcade_results")
public class ArcadeResult {

    @Id
    private String id;

    /** Reference to {@link ArcadeSession#getId()}. One result per session. */
    @Indexed(unique = true)
    private String sessionId;

    @Indexed
    private String gameCode;

    private ArcadeGameType gameType;

    private String hostCaxId;
    private String hostName;
    private String collegeId;

    /** Final standings, already sorted best-first. */
    @Builder.Default
    private List<Standing> standings = new ArrayList<>();

    /** caxIds of the winner(s). More than one on a tie. */
    @Builder.Default
    private List<String> winnerCaxIds = new ArrayList<>();

    /**
     * Flat list of every participant's caxId, indexed so "games I have played" is one
     * query against this collection instead of a scan of participants.
     */
    @Indexed
    @Builder.Default
    private List<String> participantCaxIds = new ArrayList<>();

    private int roundsPlayed;
    private int playerCount;

    private String endReason;

    private Instant startedAt;

    @Builder.Default
    private Instant endedAt = Instant.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Standing {
        private String caxId;
        private String displayName;
        private String avatarUrl;
        private int score;
        private int rank;
        private int roundsPlayed;
        private int rejoinCount;
    }
}
