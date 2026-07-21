package com.cax.cax_backend.bookmark.model;

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
@Document(collection = "bookmarks")
@CompoundIndexes({
    @CompoundIndex(name = "user_target_idx", def = "{'userId': 1, 'targetType': 1, 'targetId': 1}", unique = true)
})
public class Bookmark {
    @Id
    private String id;

    @Indexed
    private String userId;

    private BookmarkTargetType targetType;
    private String targetId;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
