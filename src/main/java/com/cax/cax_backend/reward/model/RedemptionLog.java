package com.cax.cax_backend.reward.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "redemption_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RedemptionLog {
    @Id
    private String id;
    private String userId;
    private String rewardId;
    private String rewardTitle;
    private double cost;
    private String giftCode;
    
    @Builder.Default
    private Instant redeemedAt = Instant.now();
}
