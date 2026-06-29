package com.cax.cax_backend.coins.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ad_reward_configs")
public class AdRewardConfig {

    @Id
    private String id;

    @Indexed(unique = true)
    private String adType;       // e.g. "rewarded_video", "interstitial"

    private String description;

    @Builder.Default
    private double coinsReward = 10.0;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
    private String updatedBy;
}
