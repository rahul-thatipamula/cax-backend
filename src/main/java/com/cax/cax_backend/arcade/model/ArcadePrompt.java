package com.cax.cax_backend.arcade.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * The prompt bank. Rounds draw their content from here rather than from anything the
 * client sends, which is what keeps these games endless without letting a player inject
 * arbitrary text onto everyone else's screen.
 *
 * <p>MOST_LIKELY_TO and WHO_SAID_IT rows use {@link #text}; IMPOSTER rows use {@link #text}
 * as the secret word and {@link #category} as the hint shown to the imposter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "arcade_prompts")
@CompoundIndex(name = "type_active", def = "{'gameType': 1, 'active': 1}")
public class ArcadePrompt {

    @Id
    private String id;

    @Indexed
    private ArcadeGameType gameType;

    /** The prompt text, or — for IMPOSTER — the secret word itself. */
    private String text;

    /** IMPOSTER only: the category hint handed to the imposter so they can bluff plausibly. */
    private String category;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
