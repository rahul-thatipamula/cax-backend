package com.cax.cax_backend.arcade.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One player's private input for one round: their anonymous answer in Who Said It, or
 * their one-word clue in Imposter.
 *
 * <p>{@link #caxId} is the authorship link and is the thing the whole Who Said It game is
 * about guessing, so it is stripped from the DTO until the round reveals. The
 * {@code (roundId, caxId)} unique index is what makes double-submission impossible even
 * under a replayed or concurrent request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "arcade_submissions")
@CompoundIndex(name = "round_author_unique", def = "{'roundId': 1, 'caxId': 1}", unique = true)
public class ArcadeSubmission {

    @Id
    private String id;

    /** Reference to {@link ArcadeRound#getId()}. */
    @Indexed
    private String roundId;

    /** Reference to {@link ArcadeSession#getId()}, denormalised for cheap cleanup and history. */
    @Indexed
    private String sessionId;

    private int roundNo;

    /** The author. Hidden from other players until the round is revealed. */
    private String caxId;

    /** Display name of the author, resolved at reveal time. Never sent before that. */
    private String authorName;

    /** The answer or clue. Length- and content-bounded server-side before it is stored. */
    private String content;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
