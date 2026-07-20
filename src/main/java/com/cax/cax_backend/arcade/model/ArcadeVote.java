package com.cax.cax_backend.arcade.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One player's vote in one round.
 *
 * <p>What a vote points at depends on the game: a person in Most Likely To and Imposter,
 * or an (author, submission) pairing guess in Who Said It. The {@code (roundId, voterCaxId)}
 * unique index enforces one vote per player per round at the storage layer, so ballot
 * stuffing is not something the UI has to prevent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "arcade_votes")
@CompoundIndex(name = "round_voter_unique", def = "{'roundId': 1, 'voterCaxId': 1}", unique = true)
@CompoundIndex(name = "round_target", def = "{'roundId': 1, 'targetCaxId': 1}")
public class ArcadeVote {

    @Id
    private String id;

    /** Reference to {@link ArcadeRound#getId()}. */
    @Indexed
    private String roundId;

    /** Reference to {@link ArcadeSession#getId()}. */
    @Indexed
    private String sessionId;

    private int roundNo;

    /** Who cast the vote. Always taken from the caller's token, never from the request body. */
    private String voterCaxId;

    /** The player being voted for. */
    private String targetCaxId;

    /**
     * WHO_SAID_IT only: the submission the voter is attributing to {@link #targetCaxId}.
     * Reference to {@link ArcadeSubmission#getId()}.
     */
    private String targetSubmissionId;

    /** Scored at reveal time; kept so the reveal screen can show who guessed correctly. */
    @Builder.Default
    private boolean correct = false;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
