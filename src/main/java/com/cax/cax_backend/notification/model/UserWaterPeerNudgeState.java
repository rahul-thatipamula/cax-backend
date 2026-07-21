package com.cax.cax_backend.notification.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks lifetime-capped water-reminder peer-nudge sends per user, so a
 * non-adopter is nudged at most twice, 30+ days apart.
 * See docs/engagement/WATER_REMINDER_ENGAGEMENT_2026-07-21.md — Rank 3.
 */
@Document(collection = "user_water_peer_nudge_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWaterPeerNudgeState {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    @Builder.Default
    private int nudgeCount = 0;

    private Instant lastNudgeSentAt;
}
