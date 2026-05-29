package com.cax.cax_backend.college.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "colleges")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class College {

    @Id
    private String id;

    private String collegeName;
    private String collegeCode;
    private String location;
    private String university;
    private String type;
    private String logoUrl;

    @Builder.Default
    private int studentCount = 0;
    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private Instant createdAt = Instant.now();
    private Instant updatedAt;
}
