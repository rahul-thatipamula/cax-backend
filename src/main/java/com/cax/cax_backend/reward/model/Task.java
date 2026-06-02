package com.cax.cax_backend.reward.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Task {
    @Id
    private String id;
    private String title;
    private String description;
    private double rewardCoins;
    
    @Builder.Default
    private boolean enabled = true;
    
    @Builder.Default
    private Instant createdAt = Instant.now();
}
