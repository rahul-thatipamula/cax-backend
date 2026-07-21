package com.cax.cax_backend.notification.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks the highest water-reminder adoption milestone already announced for
 * a college, so the same milestone is never re-broadcast.
 * See docs/engagement/WATER_REMINDER_ENGAGEMENT_2026-07-21.md — Rank 1.
 */
@Document(collection = "college_water_milestone_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollegeWaterMilestoneState {

    @Id
    private String id;

    @Indexed(unique = true)
    private String collegeId;

    @Builder.Default
    private int lastMilestoneNotified = 0;

    private Instant updatedAt;
}
