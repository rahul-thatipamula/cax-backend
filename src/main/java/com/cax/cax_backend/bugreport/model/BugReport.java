package com.cax.cax_backend.bugreport.model;

import com.cax.cax_backend.common.enums.BugReportEnums.*;
import com.cax.cax_backend.common.converter.InstantDeserializer;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "bugReports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BugReport {

    @Id
    private String id;

    private String userId;
    private String title;
    private String description;
    private String stepsToReproduce;

    @Builder.Default
    private BugSeverity severity = BugSeverity.MEDIUM;

    @Builder.Default
    private BugStatus status = BugStatus.OPEN;

    private String category;
    @JsonAlias("attachmentUrl")
    private String screenshotUrl;

    // Environment info
    private Map<String, String> environment;

    private String assignedTo;
    private String resolution;

    @Builder.Default
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant createdAt = Instant.now();
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant updatedAt;
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant resolvedAt;
}
