package com.cax.cax_backend.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollegeDetails {
    private String collegeId;
    private String collegeName;
    private String collegeCode;
    private String location;
}