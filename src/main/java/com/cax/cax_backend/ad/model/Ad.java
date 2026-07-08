package com.cax.cax_backend.ad.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "ads")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Ad {

    @Id
    private String id;

    @Indexed(unique = true)
    private String adId;

    private String title;
    private String imageUrl;
    private String redirectUrl;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private boolean global = true;

    @Indexed
    private String collegeId;

    @Builder.Default
    private int maxViewsPerUser = 1;

    @Builder.Default
    private int closeTimerSeconds = 0;

    @Builder.Default
    private int totalImpressions = 0;

    @Builder.Default
    private int totalClicks = 0;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @Builder.Default
    private boolean deleted = false;

    private Instant deletedAt;
}
