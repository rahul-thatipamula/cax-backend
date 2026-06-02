package com.cax.cax_backend.reward.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "referralSettings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReferralSettings {
    @Id
    @Builder.Default
    private String id = "default";
    
    @Builder.Default
    private double referralCoins = 50.0;
    
    @Builder.Default
    private Instant updatedAt = Instant.now();
    
    private String updatedBy;
}
