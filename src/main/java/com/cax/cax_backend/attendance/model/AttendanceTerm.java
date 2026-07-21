package com.cax.cax_backend.attendance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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
@Document(collection = "attendance_terms")
public class AttendanceTerm {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;

    @Builder.Default
    private List<AttendanceSubject> subjects = new ArrayList<>();

    @JsonProperty("isActive")
    @Builder.Default
    private boolean isActive = false;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
}
