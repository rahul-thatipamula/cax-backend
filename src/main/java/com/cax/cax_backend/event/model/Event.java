package com.cax.cax_backend.event.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "events")
public class Event {
    @Id
    private String id;

    @Indexed
    private String clubId;

    private String createdByUserId;
    private String name;
    private String description;
    private String logo; // Cloudflare R2 Storage URL

    // Dates
    private Instant registrationEndDate;
    private Instant eventStartDate;
    private Instant eventEndDate;

    // Payment
    @JsonProperty("isPaid")
    @Builder.Default
    private boolean isPaid = false;

    @Builder.Default
    private double fee = 0;

    private String upiId;
    private String upiQrCode; // Cloudflare R2 Storage URL of QR code image

    // Status: ACTIVE, COMPLETED, CANCELLED
    @Builder.Default
    private String status = "ACTIVE";

    @Builder.Default
    private Instant createdAt = Instant.now();

    private List<String> eventImages;

    private Instant updatedAt;
}
