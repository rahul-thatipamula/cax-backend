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
@Document(collection = "coin_transactions")
public class CoinTransaction {

    @Id
    private String id;

    @Indexed
    private String userId;

    private double amount;         // positive = earn, negative = spend

    private String type;           // "EARNED_AD" | "SPENT_BOOST"

    private String referenceId;    // adType for EARNED_AD, thoughtId for SPENT_BOOST

    private String note;

    private double balanceAfter;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
