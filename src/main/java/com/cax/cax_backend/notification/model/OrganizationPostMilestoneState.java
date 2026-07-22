package com.cax.cax_backend.notification.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks the highest like/comment milestone already announced for an
 * organization post. Mirrors {@link ThoughtEngagementMilestoneState} but for
 * {@code OrganizationPost} — the milestone is celebrated with the whole
 * organization (every member), not just the post's author.
 *
 * Kept fully separate from the core {@code OrganizationPost} model: this
 * collection exists only to gate milestone notifications and carries no
 * business/ranking meaning, so post data can be migrated or recomputed without
 * touching notification history and vice versa.
 * See docs/engagement/THOUGHT_ENGAGEMENT_MILESTONES_2026-07-21.md for the
 * pattern this reuses.
 */
@Document(collection = "organization_post_milestone_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationPostMilestoneState {

    @Id
    private String id;

    @Indexed(unique = true)
    private String postId;

    @Builder.Default
    private int lastLikeMilestoneNotified = 0;

    @Builder.Default
    private int lastCommentMilestoneNotified = 0;

    private Instant updatedAt;
}
