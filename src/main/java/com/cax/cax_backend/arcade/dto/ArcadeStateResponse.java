package com.cax.cax_backend.arcade.dto;

import com.cax.cax_backend.arcade.model.ArcadeGameType;
import com.cax.cax_backend.arcade.model.ArcadePhase;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The single payload every client polls. It is built <b>per viewer</b>: two players polling
 * the same session at the same instant get different bodies, because what each is entitled
 * to know differs.
 *
 * <p>This is the security boundary for the whole feature. The rule applied throughout is that
 * a secret is omitted from the JSON entirely rather than sent with a "don't show this" flag —
 * a field that never leaves the server cannot be recovered by patching the app, proxying the
 * traffic, or reading the response in a debugger.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArcadeStateResponse {

    private String gameCode;
    private ArcadeGameType gameType;
    private ArcadePhase phase;

    /** Bump this and clients re-render; leave it and they skip the frame entirely. */
    private long stateVersion;

    private int currentRound;
    private int totalRounds;

    /** Epoch millis the current phase auto-advances, or null for an untimed phase. */
    private Long phaseDeadlineAtMillis;

    /**
     * Server clock at the moment this payload was built. Clients time the countdown against
     * the offset from this rather than trusting the device clock, which can be wrong or set
     * deliberately to fake a longer answering window.
     */
    private long serverTimeMillis;

    private String hostCaxId;
    private String hostName;
    private boolean viewerIsHost;

    /** Everyone in the session, present or not, ordered by score. */
    @Builder.Default
    private List<PlayerView> players = new ArrayList<>();

    private int presentCount;
    private int totalCount;

    /** How many present players have completed the action this phase is waiting on. */
    private int readyCount;

    /** The current round, redacted for this viewer. Null in LOBBY and FINISHED. */
    private RoundView round;

    /** The viewer's own private state — always safe to send, it is their own data. */
    private MeView me;

    /** Final standings, only once the session is FINISHED. */
    private FinalView finalResult;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlayerView {
        private String caxId;
        private String displayName;
        private String avatarUrl;
        private int score;
        private boolean host;

        /** Derived from the presence window — this is the "who's here / who's left" signal. */
        private boolean present;

        /** True if they have come back after dropping out; the UI badges them as rejoined. */
        private boolean rejoined;

        /**
         * Whether they have acted this phase. Deliberately a boolean and not the action
         * itself: the lobby needs to show "8 of 10 have answered" without leaking what
         * anyone answered or who they voted for.
         */
        private boolean acted;

        /** Ready for the next round during INTERMISSION. */
        private boolean ready;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RoundView {
        private int roundNo;

        /** The shared prompt. Absent for Imposter, which has no public prompt. */
        private String prompt;

        /**
         * Imposter only, and only for players who are <b>not</b> the imposter. The imposter's
         * payload simply does not contain this field, so there is nothing for a modified
         * client to reveal.
         */
        private String secretWord;

        /** Imposter only: the category hint, sent to the imposter in place of the word. */
        private String secretCategory;

        /** Imposter only: tells the viewer about themselves, and about nobody else. */
        private Boolean viewerIsImposter;

        /**
         * The round's submissions. Author fields stay null until the round reveals — that
         * concealment is the entire Who Said It game, so it is enforced here rather than
         * by the UI choosing not to render a field it was given.
         */
        @Builder.Default
        private List<SubmissionView> submissions = new ArrayList<>();

        /** Tallies, released only at REVEAL so live voting cannot be influenced by a leaked count. */
        @Builder.Default
        private List<VoteTally> tallies = new ArrayList<>();

        /** REVEAL only: who the imposter turned out to be. */
        private String imposterCaxId;

        /** REVEAL only. */
        @Builder.Default
        private List<String> winnerCaxIds = new ArrayList<>();

        /** REVEAL only: one-line description of the outcome. */
        private String outcomeSummary;

        /** REVEAL only: what each player earned this round, for the score-change animation. */
        @Builder.Default
        private List<ScoreDelta> deltas = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SubmissionView {
        private String id;
        private String content;

        /** Null until REVEAL. */
        private String authorCaxId;

        /** Null until REVEAL. */
        private String authorName;

        /** True if this is the viewer's own submission — they already know they wrote it. */
        private boolean mine;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoteTally {
        private String caxId;
        private String displayName;
        private int votes;

        /** caxIds of the people who voted for this target. REVEAL only. */
        @Builder.Default
        private List<String> voterCaxIds = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreDelta {
        private String caxId;
        private String displayName;
        private int delta;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MeView {
        private String caxId;
        private String displayName;
        private int score;
        private int rank;
        private boolean host;
        private boolean submitted;
        private boolean voted;
        private boolean ready;

        /** The viewer's own submission text, echoed back so a rejoiner sees what they wrote. */
        private String mySubmission;

        /** Who the viewer voted for this round. Their own ballot, so safe to return. */
        private String myVoteTargetCaxId;
        private String myVoteSubmissionId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FinalView {
        @Builder.Default
        private List<Standing> standings = new ArrayList<>();

        @Builder.Default
        private List<String> winnerCaxIds = new ArrayList<>();

        private String winnerLine;
        private int roundsPlayed;
        private String endReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Standing {
        private int rank;
        private String caxId;
        private String displayName;
        private String avatarUrl;
        private int score;
        private int roundsPlayed;
    }
}
