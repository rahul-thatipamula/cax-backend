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
 * Dedicated analytics & interaction tracking model for Bulletin Events.
 * Maintained in its own MongoDB collection ('bulletin_event_analytics')
 * to maintain strict separation of concerns from core business models.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bulletin_event_analytics")
public class BulletinEventAnalytics {

    @Id
    private String id;

    @Indexed(unique = true)
    private String bulletinEventId;

    @Builder.Default
    private long webImpressionsCount = 0;

    @Builder.Default
    private long webAppImpressionsCount = 0;

    @Builder.Default
    private long webDetailViewsCount = 0;

    @Builder.Default
    private long webAppViewsCount = 0;

    @Builder.Default
    private long appViewsCount = 0;

    @Builder.Default
    private long externalLinkClicksCount = 0;

    @Builder.Default
    private double engagementScore = 0.0;

    private Instant lastCalculatedAt;

    private Instant createdAt;

    private Instant updatedAt;
}
