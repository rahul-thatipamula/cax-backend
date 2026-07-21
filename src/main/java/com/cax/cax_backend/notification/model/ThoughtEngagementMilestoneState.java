package com.cax.cax_backend.notification.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks the highest like/comment milestone already announced to a thought's
 * author. Kept fully separate from core business models (Thought,
 * ThoughtEngagementScore) — this collection exists only to gate milestone
 * notifications and carries no scoring/ranking meaning.
 * See docs/engagement/WATER_REMINDER_ENGAGEMENT_2026-07-21.md for the
 * equivalent pattern used by the water-reminder college milestone feature.
 */
@Document(collection = "thought_engagement_milestone_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThoughtEngagementMilestoneState {

    @Id
    private String id;

    @Indexed(unique = true)
    private String thoughtId;

    @Builder.Default
    private int lastLikeMilestoneNotified = 0;

    @Builder.Default
    private int lastCommentMilestoneNotified = 0;

    private Instant updatedAt;
}
