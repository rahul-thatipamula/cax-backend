package com.cax.cax_backend.organization.model;

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
@Document(collection = "ripples")
public class Ripple {
    @Id
    private String id;

    @Indexed
    private String organizationId;

    @Indexed
    private String creatorId;

    private String creatorName;
    private String creatorPicture;
    private String creatorRole;

    private String content;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private boolean deleted = false;

    private Instant deletedAt;
}
