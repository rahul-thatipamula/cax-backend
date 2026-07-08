package com.cax.cax_backend.collegereport.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "collegeReports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollegeReport {

    @Id
    private String id;

    private String email;
    private String domain;
    private String name;
    private String picture;

    private String detectedCollegeName;
    private String detectedCollegeId;

    private String reason;

    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Builder.Default
    private Instant createdAt = Instant.now();
    private Instant resolvedAt;
    private String adminNote;

    @Builder.Default
    private boolean deleted = false;
    private Instant deletedAt;

    public enum ReportStatus {
        PENDING, RESOLVED, DISMISSED
    }
}
