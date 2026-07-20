package com.cax.cax_backend.arcade.dto;

import com.cax.cax_backend.arcade.model.ArcadeGameType;
import com.cax.cax_backend.arcade.model.ArcadePhase;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only projections for the Arcade hub, the played-games history and the per-user
 * profile stats. These never expose round secrets: a finished game's history shows the
 * standings and the outcome, not the prompts-and-answers trail of a live round.
 */
public class ArcadeHistoryDtos {

    /** A game the viewer can resume — shown on the hub so a dropped player can get back in. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ActiveSessionCard {
        private String gameCode;
        private ArcadeGameType gameType;
        private ArcadePhase phase;
        private int currentRound;
        private int totalRounds;
        private int playerCount;
        private int presentCount;
        private boolean viewerIsHost;

        /** True when the viewer has a participant row but is not currently present. */
        private boolean canRejoin;

        private String hostName;
        private Instant createdAt;
    }

    /** One finished game in the history list. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HistoryEntry {
        private String sessionId;
        private String gameCode;
        private ArcadeGameType gameType;
        private String hostName;
        private int playerCount;
        private int roundsPlayed;

        /** The viewer's own outcome in this game. */
        private int myScore;
        private int myRank;
        private boolean iWon;

        private String winnerLine;

        @Builder.Default
        private List<ArcadeStateResponse.Standing> standings = new ArrayList<>();

        private Instant endedAt;
    }

    /** Lifetime aggregates for the profile screen. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserStats {
        private int gamesPlayed;
        private int gamesWon;
        private int totalScore;
        private int bestScore;

        /** Win rate as a whole percentage, computed server-side so clients agree on it. */
        private int winRatePercent;

        /** Per-game-type breakdown, so the profile can show which game someone is best at. */
        @Builder.Default
        private List<GameTypeStat> byGameType = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameTypeStat {
        private ArcadeGameType gameType;
        private int played;
        private int won;
        private int bestScore;
    }
}
