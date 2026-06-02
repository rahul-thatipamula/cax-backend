package com.cax.cax_backend.reward.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "user_referrals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserReferral {
    @Id
    private String id; // Maps to User's userId

    @Indexed(unique = true)
    private String referralCode;

    private String referredBy; // Maps to Referrer's userId
    
    @Builder.Default
    private Instant createdAt = Instant.now();
}
