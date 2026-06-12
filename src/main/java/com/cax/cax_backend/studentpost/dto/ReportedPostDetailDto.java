package com.cax.cax_backend.studentpost.dto;

import com.cax.cax_backend.studentpost.model.StudentPost;
import com.cax.cax_backend.studentpost.model.ThoughtReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportedPostDetailDto {
    private StudentPost post;
    private long reportCount;
    private List<ThoughtReport> reports;
}
