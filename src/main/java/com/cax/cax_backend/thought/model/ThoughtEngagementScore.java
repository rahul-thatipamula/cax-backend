package com.cax.cax_backend.thought.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "thought_engagement_scores")
@CompoundIndexes({
    @CompoundIndex(name = "global_score_idx", def = "{'engagementScore': -1}"),
    @CompoundIndex(name = "college_score_idx", def = "{'collegeId': 1, 'engagementScore': -1}"),
    @CompoundIndex(name = "postId_unique_idx", def = "{'thoughtId': 1}", unique = true)
})
public class ThoughtEngagementScore {

    @Id
    private String id;

    private String thoughtId;

    private String collegeId;
    private String authorUserId;

    @Builder.Default
    private int likeCount = 0;

    @Builder.Default
    private int commentCount = 0;

    @Builder.Default
    private int viewCount = 0;

    @Builder.Default
    private double engagementScore = 0.0;

    private Instant thoughtCreatedAt;

    @Builder.Default
    private Instant lastComputedAt = Instant.now();

    /**
     * Set once when the author is notified that this thought is trending.
     * Null means the author has never been notified. Used to enforce the
     * "notify only once per thought" rule.
     */
    private Instant trendingNotifiedAt;
}
