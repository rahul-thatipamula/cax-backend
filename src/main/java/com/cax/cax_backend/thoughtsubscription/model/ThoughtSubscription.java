package com.cax.cax_backend.thoughtsubscription.model;

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
@Document(collection = "thought_subscriptions")
@CompoundIndexes({
    @CompoundIndex(name = "subscriber_author_idx", def = "{'subscriberId': 1, 'authorId': 1}", unique = true)
})
public class ThoughtSubscription {
    @Id
    private String id;

    @Indexed
    private String subscriberId;

    @Indexed
    private String authorId;

    private String authorName;
    private String authorPicture;

    @Builder.Default
    private Instant subscribedAt = Instant.now();

    // Soft-deleted on unsubscribe so the record (and its notification history)
    // survives repeated subscribe/unsubscribe toggling, letting us cool down
    // "new subscriber" notifications instead of re-spamming the author.
    @Builder.Default
    private boolean active = true;

    private Instant unsubscribedAt;
    private Instant lastNotifiedAt;
}
