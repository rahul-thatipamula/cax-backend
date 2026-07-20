package com.cax.cax_backend.arcade.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One round of one session, holding the round's content and — critically — its secrets.
 *
 * <p><b>This document must never be serialised to a client.</b> {@link #secretWord} and
 * {@link #imposterCaxId} decide the entire Imposter game, so they are only ever exposed
 * through a per-viewer DTO that redacts them based on who is asking. Returning this entity
 * from a controller would hand every player the answer, which no amount of client-side
 * hiding could fix.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "arcade_rounds")
@CompoundIndex(name = "session_round_unique", def = "{'sessionId': 1, 'roundNo': 1}", unique = true)
public class ArcadeRound {

    @Id
    private String id;

    /** Reference to {@link ArcadeSession#getId()}. */
    @Indexed
    private String sessionId;

    private ArcadeGameType gameType;

    /** 1-based round number within the session. */
    private int roundNo;

    /** The prompt shown to everyone. Used by MOST_LIKELY_TO and WHO_SAID_IT. */
    private String prompt;

    // ── IMPOSTER secrets — redacted per viewer, never sent wholesale ────────────

    /** The word every player except the imposter receives. IMPOSTER only. */
    private String secretWord;

    /** Category hint the imposter is given so they can bluff. IMPOSTER only. */
    private String secretCategory;

    /** The caxId of the player who was not told the word. IMPOSTER only. */
    private String imposterCaxId;

    // ── Outcome, filled in at REVEAL ───────────────────────────────────────────

    /** caxIds that won this round (ties are possible, so this is a list). */
    @Builder.Default
    private List<String> winnerCaxIds = new ArrayList<>();

    /** Human-readable one-line summary of what happened, rendered on the reveal screen. */
    private String outcomeSummary;

    /**
     * What each player earned this round, kept so the reveal screen can show the score change
     * to a player who polls after the fact — including someone who rejoined mid-reveal and
     * has no earlier total to diff against.
     */
    @Builder.Default
    private java.util.Map<String, Integer> scoreDeltas = new java.util.LinkedHashMap<>();

    /** Short reason shown next to each delta, e.g. "Fooled someone". */
    @Builder.Default
    private java.util.Map<String, String> deltaReasons = new java.util.LinkedHashMap<>();

    @Builder.Default
    private boolean revealed = false;

    @Builder.Default
    private Instant startedAt = Instant.now();

    private Instant endedAt;
}
