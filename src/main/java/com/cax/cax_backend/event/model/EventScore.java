package com.cax.cax_backend.event.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Ranking score for an event, tracked in its own collection separate from the Event document. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "event_scores")
public class EventScore {
    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    @Indexed
    private String organizationId;

    /** Denormalized from the event, so past (completed) events can be queried for inheritance without a join. */
    private Instant eventEndDate;

    /** Starting score inherited from the organization's past events, before any joins. */
    @Builder.Default
    private double baseScore = 0;

    /** Participant count last used to compute {@code score}. */
    @Builder.Default
    private long joinedCount = 0;

    /** Current ranking score: baseScore + the joinedCount contribution. */
    @Builder.Default
    private double score = 0;

    private Instant createdAt;
    private Instant updatedAt;
}
