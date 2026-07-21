package com.cax.cax_backend.attendance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Embedded inside an AttendanceTerm document — not a standalone collection. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSubject {
    private String id;
    private String name;
    private int attended;
    private int total;
    private double targetPercentage;
}
