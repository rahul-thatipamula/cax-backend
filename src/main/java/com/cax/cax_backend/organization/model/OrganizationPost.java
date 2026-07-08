package com.cax.cax_backend.organization.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "organization_posts")
public class OrganizationPost {
    @Id
    private String id;

    @Indexed
    private String organizationId;

    private String organizationName;
    private String clubLogo;

    @Indexed
    private String collegeId;

    @Indexed
    private String creatorId;

    private String caption;

    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Builder.Default
    private List<String> likes = new ArrayList<>(); // list of user IDs

    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @com.fasterxml.jackson.annotation.JsonProperty("isPoll")
    private boolean isPoll;
    private String pollQuestion;

    @Builder.Default
    private List<PollOption> pollOptions = new ArrayList<>();

    @Builder.Default
    private boolean deleted = false;

    private Instant deletedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PollOption {
        private String optionId;
        private String text;

        @Builder.Default
        private List<String> votes = new ArrayList<>(); // list of user IDs
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Comment {
        private String id;
        private String userId;
        private String userName;
        private String userPicture;
        private String text;
        private Instant createdAt;
    }
}
