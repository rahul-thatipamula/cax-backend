package com.cax.cax_backend.boost.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "thought_boost_requests")
@CompoundIndexes({
    @CompoundIndex(name = "status_requested_idx", def = "{'status': 1, 'requestedAt': 1}"),
    @CompoundIndex(name = "user_thought_idx", def = "{'userId': 1, 'thoughtId': 1}")
})
public class ThoughtBoostRequest {

    @Id
    private String id;

    @Indexed
    private String thoughtId;

    @Indexed
    private String userId;

    private String collegeId;
    private String thoughtHeading;

    @Builder.Default
    private double coinsSpent = 50.0;

    @Builder.Default
    private BoostStatus status = BoostStatus.PENDING;

    @Builder.Default
    private Instant requestedAt = Instant.now();

    private Instant activatedAt;
    private Instant completedAt;
}
