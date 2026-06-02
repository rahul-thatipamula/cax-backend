package com.cax.cax_backend.club.model;

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
@Document(collection = "club_join_requests")
public class ClubJoinRequest {
    @Id
    private String id;

    @Indexed
    private String clubId;

    @Indexed
    private String userId;

    private String name;
    private String email;
    private String picture;

    @Builder.Default
    private String status = "PENDING"; // "PENDING", "ACCEPTED", "REJECTED"

    private String paymentScreenshot;
    private String utr;
    private Double amountPaid;

    @Builder.Default
    private Instant requestedAt = Instant.now();
}
