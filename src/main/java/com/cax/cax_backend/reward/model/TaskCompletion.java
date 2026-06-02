package com.cax.cax_backend.reward.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "task_completions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskCompletion {
    @Id
    private String id;
    private String userId;
    private String taskId;
    
    @Builder.Default
    private Instant claimedAt = Instant.now();
}
