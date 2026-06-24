package com.cax.cax_backend.thought.model;

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
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "thoughts")
@CompoundIndexes({
    @CompoundIndex(name = "college_created_idx", def = "{'collegeId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "user_created_idx", def = "{'userId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "active_college_created_idx", def = "{'collegeId': 1, 'disabled': 1, 'createdAt': -1}")
})
public class Thought {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String creatorName;
    private String creatorPicture;

    @Indexed
    private String collegeId;

    private String collegeName;

    private String heading;
    private String content;
    private String sharedLink;

    @Builder.Default
    private List<ThoughtImage> images = new ArrayList<>();

    @Builder.Default
    private List<String> likes = new ArrayList<>();

    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @Builder.Default
    private List<ThoughtHistory> history = new ArrayList<>();

    @Builder.Default
    private boolean disabled = false;

    @Builder.Default
    private boolean creatorVerified = false;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThoughtImage {
        private String url;
        @Builder.Default
        private String alignment = "center";
        @Builder.Default
        private double widthRatio = 1.0;
        @Builder.Default
        private int insertAfterLine = 0;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThoughtHistory {
        private String heading;
        private String content;
        private List<ThoughtImage> images;
        private Instant editedAt;
    }
}
