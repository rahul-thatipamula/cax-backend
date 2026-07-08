package com.cax.cax_backend.thought.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "thought_reports")
public class ThoughtReport {
    @Id
    private String id;

    @Indexed
    private String postId;

    @Indexed
    private String reporterUserId;

    private String reporterName;
    private String reporterEmail;

    private String reason;

    @Builder.Default
    private Instant reportedAt = Instant.now();

    @Builder.Default
    private boolean deleted = false;

    private Instant deletedAt;
}
