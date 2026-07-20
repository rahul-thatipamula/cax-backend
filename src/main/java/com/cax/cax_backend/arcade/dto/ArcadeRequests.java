package com.cax.cax_backend.arcade.dto;

import com.cax.cax_backend.arcade.model.ArcadeGameType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Every inbound payload the Arcade accepts.
 *
 * <p>Note what is deliberately absent: no request carries a caxId, a score, a round number
 * to act on, or a phase. The caller's identity comes from their token and the round they are
 * acting on is whatever the server currently has open — so a tampered client cannot act as
 * someone else, award itself points, or replay a request against an older round.
 */
public class ArcadeRequests {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSession {
        private ArcadeGameType gameType;

        /** Optional; clamped server-side to the allowed range. */
        private Integer totalRounds;

        /** Optional score-based end condition; clamped server-side. */
        private Integer targetScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Submit {
        /** The answer (Who Said It) or one-word clue (Imposter). Trimmed and bounded server-side. */
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vote {
        /** The player being voted for. Validated to be a real participant of this session. */
        private String targetCaxId;

        /** Who Said It only: the submission being attributed to {@code targetCaxId}. */
        private String targetSubmissionId;
    }
}
