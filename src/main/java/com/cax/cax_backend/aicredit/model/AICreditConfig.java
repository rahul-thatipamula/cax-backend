package com.cax.cax_backend.aicredit.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "aiCreditConfig")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AICreditConfig {
    @Id @Builder.Default private String id = "default";
    @Builder.Default private int aiSummaryCost = 5;
    @Builder.Default private int aiProSummaryCost = 10;
    @Builder.Default private boolean aiSummaryEnabled = true;
    @Builder.Default private boolean signupCreditsEnabled = true;
    @Builder.Default private Instant updatedAt = Instant.now();
    private String updatedBy;
}
