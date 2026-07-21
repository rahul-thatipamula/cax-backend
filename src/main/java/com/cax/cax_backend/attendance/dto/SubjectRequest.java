package com.cax.cax_backend.attendance.dto;

import lombok.Data;

@Data
public class SubjectRequest {
    private String name;
    private int attended;
    private int total;
    private double targetPercentage;
}
