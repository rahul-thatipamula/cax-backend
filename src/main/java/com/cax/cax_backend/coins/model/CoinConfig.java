package com.cax.cax_backend.coins.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "coin_config")
public class CoinConfig {

    // Fixed ID so there is only ever one config document in the collection.
    public static final String SINGLETON_ID = "global";

    @Id
    private String id;

    @Builder.Default
    private double boostCost = 50.0;

    private Instant updatedAt;
    private String updatedBy;
}
