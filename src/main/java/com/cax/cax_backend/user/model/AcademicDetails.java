package com.cax.cax_backend.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicDetails {
    private int admissionBatch;
    private int currentAcademicYear;
    private int currentSemester;
    private Instant updatedAt;
}
