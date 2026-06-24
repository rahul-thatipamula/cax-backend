package com.cax.cax_backend.thought.dto;

import com.cax.cax_backend.thought.model.Thought;
import com.cax.cax_backend.thought.model.ThoughtReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportedThoughtDetailDto {
    private Thought thought;
    private long reportCount;
    private List<ThoughtReport> reports;
}
