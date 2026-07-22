package com.cax.cax_backend.event.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Dedicated analytics & interaction tracking model for standard Events.
 * Maintained in its own MongoDB collection ('event_analytics')
 * to maintain strict separation of concerns from core business models.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "event_analytics")
public class EventAnalytics {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    @Indexed
    private String collegeId;

    @Builder.Default
    private boolean isGlobal = false;

    @Builder.Default
    private long detailViewsCount = 0;

    @Builder.Default
    private long joinedCount = 0;

    @Builder.Default
    private long sharesCount = 0;

    @Builder.Default
    private long externalClicksCount = 0;

    @Builder.Default
    private double internalScore = 0.0;

    @Builder.Default
    private double globalScore = 0.0;

    private Instant lastCalculatedAt;

    private Instant createdAt;

    private Instant updatedAt;
}
