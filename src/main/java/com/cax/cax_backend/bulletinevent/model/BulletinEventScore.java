package com.cax.cax_backend.bulletinevent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Ranking score for a bulletin event, tracked in its own collection, separate from event scoring.
 * Bulletins are externally managed (no CAX participants and no organizing club), so there is
 * nothing to inherit from and no join count to grow the score — it starts and stays at 0 until
 * a future ranking signal for bulletins is introduced.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bulletin_event_scores")
public class BulletinEventScore {
    @Id
    private String id;

    @Indexed(unique = true)
    private String bulletinEventId;

    @Builder.Default
    private double score = 0;

    private Instant createdAt;
    private Instant updatedAt;
}
